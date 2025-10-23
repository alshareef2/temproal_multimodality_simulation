import os, argparse, numpy as np, torch, json as _json
from data import load_csv_series
from model import TemporalDDPM
from viz import plot_timeseries_overlay, plot_mask_heatmap, plot_mae_bars
from metrics import masked_mae, masked_mse
def simulate_drop(mask_shape, mode='bernoulli', p=0.25, ge=None, seed=0):
    np.random.seed(seed); T,D=mask_shape
    if mode=='bernoulli': return (np.random.rand(T,D) > p).astype(np.float32)
    elif mode=='gilbert_elliott':
        mask=np.ones((T,D),dtype=np.float32); pG,pB,eta,rho=ge['pG'],ge['pB'],ge['eta'],ge['rho']; state='G'
        for t in range(T):
            if state=='G' and np.random.rand()<eta: state='B'
            elif state=='B' and np.random.rand()<rho: state='G'
            p_drop=pG if state=='G' else pB; mask[t]=(np.random.rand(D)>p_drop).astype(np.float32)
        return mask
    else: return np.ones((T,D),dtype=np.float32)
def main(config='config.json', ckpt='runs/ckpt.pt', out_dir='outputs'):
    os.makedirs(out_dir, exist_ok=True); cfg=_json.load(open(config)); ck=torch.load(ckpt,map_location='cpu'); feats=ck['features']; norm=ck['norm']
    ts, X_filled, Mnat, _ = load_csv_series(cfg['data']['csv'], cfg['data']['timestamp_col'], feats or cfg['data']['feature_cols'])
    if norm is not None:
        mean=np.array(norm['mean']); std=np.array(norm['std']); Xn=(X_filled-mean)/std; Xn=Xn*Mnat
    else: Xn=X_filled
    T = min(cfg['data']['window_len'], len(Xn))
    gt = Xn[:T]; mnat=Mnat[:T]
    sim=cfg['sim']; msim=simulate_drop((T,gt.shape[1]), mode=sim['drop'], p=sim['bernoulli_p'], ge=sim['ge'], seed=42)
    mobs=(mnat*msim).astype(np.float32); obs=gt*mobs
    device=torch.device(cfg['train']['device'] if torch.cuda.is_available() else 'cpu')
    model=TemporalDDPM(data_dim=gt.shape[1], hidden=cfg['model']['hidden'], depth=cfg['model']['depth'], timesteps=cfg['model']['timesteps'], schedule=cfg['model']['schedule']).to(device=device, dtype=torch.float32)
    model.load_state_dict(ck['state_dict']); model.eval()
    x_obs=torch.from_numpy(obs.astype('float32')).to(device).unsqueeze(0); mobs_t=torch.from_numpy(mobs.astype('float32')).to(device).unsqueeze(0); mmiss=1.0-mobs_t; cond=torch.cat([x_obs,mobs_t],dim=-1).to(torch.float32)
    with torch.no_grad(): recon=model.ddim_impute(x_obs, mmiss, cond, steps=cfg['model']['sampler_steps']).squeeze(0).cpu().numpy()
    if norm is not None: gt_den=gt*std+mean; recon_den=recon*std+mean; obs_den=obs*std+mean
    else: gt_den, recon_den, obs_den = gt, recon, obs
    valid=(mnat==1.0)&(mobs==0.0)
    MAE=masked_mae(recon_den, gt_den, valid.astype(np.float32)); MSE=masked_mse(recon_den, gt_den, valid.astype(np.float32))
    MAE_per_feature=[]; 
    for d in range(gt_den.shape[1]):
        md=valid[:,d].astype(np.float32); MAE_per_feature.append(0.0 if md.sum()==0 else masked_mae(recon_den[:,d], gt_den[:,d], md))
    import json as __json; __json.dump({"MAE":MAE,"MSE":MSE,"MAE_per_feature":MAE_per_feature}, open(os.path.join(out_dir,"metrics.json"),"w"), indent=2)
    plot_obs=obs_den.copy(); plot_obs[mobs==0.0]=np.nan; plot_rec=recon_den.copy(); plot_rec[mobs==1.0]=np.nan
    ts_axis=np.arange(len(gt_den)); feat_indices=list(range(gt_den.shape[1]))
    plot_timeseries_overlay(ts_axis, gt_den, plot_obs, plot_rec, feat_indices, os.path.join(out_dir,"recon_timeseries.png"), max_points=cfg['viz']['max_points_plot'])
    plot_mask_heatmap(mobs, os.path.join(out_dir,"mask_heatmap.png"))
    plot_mae_bars(MAE_per_feature, os.path.join(out_dir,"mae_per_feature.png"))
    print("Saved to", out_dir)
if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('--config',type=str,default='config.json'); ap.add_argument('--ckpt',type=str,default='runs/ckpt.pt'); ap.add_argument('--out_dir',type=str,default='outputs'); args=ap.parse_args(); main(args.config,args.ckpt,args.out_dir)
