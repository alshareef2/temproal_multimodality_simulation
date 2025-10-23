# simulate_eval.py
import os
import argparse
import numpy as np
import torch
import json as _json

from data import load_csv_series
from model import TemporalDDPM
from viz import plot_timeseries_overlay, plot_mask_heatmap, plot_mae_bars
from metrics import masked_mae, masked_mse


def simulate_drop(mask_shape, mode='bernoulli', p=0.25, ge=None, seed=0):
    """Create a simulated availability mask: 1=kept (observed), 0=dropped."""
    np.random.seed(seed)
    T, D = mask_shape
    if mode == 'bernoulli':
        return (np.random.rand(T, D) > p).astype(np.float32)
    elif mode == 'gilbert_elliott':
        mask = np.ones((T, D), dtype=np.float32)
        pG, pB, eta, rho = ge['pG'], ge['pB'], ge['eta'], ge['rho']
        state = 'G'
        for t in range(T):
            if state == 'G' and np.random.rand() < eta:
                state = 'B'
            elif state == 'B' and np.random.rand() < rho:
                state = 'G'
            p_drop = pG if state == 'G' else pB
            mask[t] = (np.random.rand(D) > p_drop).astype(np.float32)
        return mask
    else:
        return np.ones((T, D), dtype=np.float32)


@torch.no_grad()
def causal_rollout(model, gt, mnat, msim, sampler_steps=30, context_len=None, device='cpu'):
    """
    Causal imputation: reconstruct x[t] using only {0..t-1} context.
    gt   : [T, D] (normalized) ground-truth values (zeros where natural NaN)
    mnat : [T, D] natural-availability mask (1 if CSV had a value)
    msim : [T, D] simulated-keep mask (1=kept, 0=dropped)
    Returns:
        recon : [T, D] reconstructed sequence (normalized space)
        mobs_final : [T, D] final observed mask after committing imputations
    """
    T, D = gt.shape
    mobs0 = (mnat * msim).astype(np.float32)  # original observed mask (for eval/plots)
    obs = gt * mobs0                          # working buffer with observed values
    mobs_sofar = mobs0.copy()

    for t in range(T):
        # choose local context window [s:e) ending at t
        s = 0 if context_len is None else max(0, t - context_len + 1)
        e = t + 1

        obs_w = obs[s:e].copy()
        mobs_w = mobs_sofar[s:e].copy()

        # Only impute at the last row
        miss_row = (1.0 - mobs_w[-1:])
        if miss_row.sum() == 0:
            continue

        x_obs = torch.from_numpy(obs_w.astype('float32')).unsqueeze(0).to(device)   # [1, Tw, D]
        m_obs = torch.from_numpy(mobs_w.astype('float32')).unsqueeze(0).to(device)  # [1, Tw, D]
        cond = torch.cat([x_obs, m_obs], dim=-1).to(torch.float32)

        mask_miss = np.zeros_like(mobs_w, dtype=np.float32)
        mask_miss[-1:] = miss_row
        mask_miss_t = torch.from_numpy(mask_miss).unsqueeze(0).to(device)

        x_hat_w = model.ddim_impute(x_obs, mask_miss_t, cond, steps=sampler_steps)  # [1, Tw, D]
        x_hat_w = x_hat_w.squeeze(0).cpu().numpy()

        # Commit only where natural GT exists and was originally dropped
        recon_row = x_hat_w[-1]
        commit_mask = (mnat[t] == 1.0) & (mobs_sofar[t] == 0.0)
        obs[t, commit_mask] = recon_row[commit_mask]
        mobs_sofar[t, commit_mask] = 1.0

    return obs, mobs_sofar


def main(config='config.json', ckpt='runs/ckpt.pt', out_dir='outputs'):
    os.makedirs(out_dir, exist_ok=True)
    cfg = _json.load(open(config))
    ck = torch.load(ckpt, map_location='cpu')
    feats = ck['features']
    norm = ck['norm']

    # Load & normalize like training
    ts, X_filled, Mnat, _ = load_csv_series(
        cfg['data']['csv'],
        cfg['data']['timestamp_col'],
        feats or cfg['data']['feature_cols']
    )
    if norm is not None:
        mean = np.array(norm['mean'])
        std = np.array(norm['std'])
        Xn = (X_filled - mean) / std
        Xn = Xn * Mnat
    else:
        Xn = X_filled
        mean = std = None

    # Pick a window for demo/plots
    T = min(cfg['data']['window_len'], len(Xn))
    gt = Xn[:T]
    mnat = Mnat[:T]

    # Simulate drops
    sim = cfg.get('sim', {"drop": "bernoulli", "bernoulli_p": 0.25, "ge": {"pG": 0.02, "pB": 0.45, "eta": 0.1, "rho": 0.25}})
    msim = simulate_drop((T, gt.shape[1]),
                         mode=sim.get('drop', 'bernoulli'),
                         p=sim.get('bernoulli_p', 0.25),
                         ge=sim.get('ge'),
                         seed=42)

    # ORIGINAL observed mask (natural ∧ simulated) — keep for metrics/plots
    mobs0 = (mnat * msim).astype(np.float32)

    # Model
    device = torch.device(cfg['train']['device'] if torch.cuda.is_available() else 'cpu')
    model = TemporalDDPM(
        data_dim=gt.shape[1],
        hidden=cfg['model']['hidden'],
        depth=cfg['model']['depth'],
        timesteps=cfg['model']['timesteps'],
        schedule=cfg['model']['schedule']
    ).to(device=device, dtype=torch.float32)
    model.load_state_dict(ck['state_dict'])
    model.eval()

    # Eval mode
    eval_cfg = cfg.get('eval', {})
    causal = bool(eval_cfg.get('causal', False))
    sampler_steps = int(eval_cfg.get('sampler_steps', cfg['model']['sampler_steps']))
    context_len = eval_cfg.get('context_len', None)

    if causal:
        # Causal: step forward in time, committing each imputed value
        recon_norm, mobs_final = causal_rollout(
            model, gt, mnat, msim,
            sampler_steps=sampler_steps,
            context_len=context_len,
            device=device
        )
    else:
        # Bidirectional: impute using full window (can use future)
        obs = gt * mobs0
        x_obs = torch.from_numpy(obs.astype('float32')).to(device).unsqueeze(0)
        mobs_t = torch.from_numpy(mobs0.astype('float32')).to(device).unsqueeze(0)
        mmiss = 1.0 - mobs_t
        cond = torch.cat([x_obs, mobs_t], dim=-1).to(torch.float32)
        with torch.no_grad():
            recon_norm = model.ddim_impute(x_obs, mmiss, cond, steps=sampler_steps).squeeze(0).cpu().numpy()
        mobs_final = mobs0  # not used further, but set for symmetry

    # De-normalize for reporting
    if norm is not None:
        gt_den = gt * std + mean
        recon_den = recon_norm * std + mean
        obs_den = (gt * mobs0) * std + mean
    else:
        gt_den, recon_den, obs_den = gt, recon_norm, (gt * mobs0)

    # Metrics ONLY where we originally dropped and had GT
    valid = (mnat == 1.0) & (mobs0 == 0.0)
    MAE = masked_mae(recon_den, gt_den, valid.astype(np.float32))
    MSE = masked_mse(recon_den, gt_den, valid.astype(np.float32))
    MAE_per_feature = []
    for d in range(gt_den.shape[1]):
        md = valid[:, d].astype(np.float32)
        MAE_per_feature.append(0.0 if md.sum() == 0 else masked_mae(recon_den[:, d], gt_den[:, d], md))

    # Save metrics
    _json.dump(
        {"MAE": MAE, "MSE": MSE, "MAE_per_feature": MAE_per_feature, "causal": causal},
        open(os.path.join(out_dir, "metrics.json"), "w"),
        indent=2
    )

    # Plots: use ORIGINAL mask for gaps & recon visibility
    plot_obs = obs_den.copy()
    plot_obs[mobs0 == 0.0] = np.nan

    plot_rec = recon_den.copy()
    plot_rec[mobs0 == 1.0] = np.nan  # show recon only where we originally dropped

    ts_axis = np.arange(len(gt_den))
    feat_indices = list(range(gt_den.shape[1]))
    plot_timeseries_overlay(
        ts_axis, gt_den, plot_obs, plot_rec,
        feat_indices, os.path.join(out_dir, "recon_timeseries.png"),
        max_points=cfg['viz']['max_points_plot']
    )
    plot_mask_heatmap(mobs0, os.path.join(out_dir, "mask_heatmap.png"))
    plot_mae_bars(MAE_per_feature, os.path.join(out_dir, "mae_per_feature.png"))

    print(f"Saved to {out_dir}  (causal={causal})")


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--config', type=str, default='config.json')
    ap.add_argument('--ckpt', type=str, default='runs/ckpt.pt')
    ap.add_argument('--out_dir', type=str, default='outputs')
    args = ap.parse_args()
    main(args.config, args.ckpt, args.out_dir)
