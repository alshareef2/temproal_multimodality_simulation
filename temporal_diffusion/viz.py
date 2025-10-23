import matplotlib.pyplot as plt, numpy as np
def plot_timeseries_overlay(ts, gt, obs, recon, feat_indices, out_png, max_points=2000):
    T=gt.shape[0]; idx=np.arange(T) if T<=max_points else np.linspace(0,T-1,max_points).astype(int); x=ts[idx] if ts is not None else np.arange(len(idx))
    for d in feat_indices:
        plt.figure(); plt.plot(x, gt[idx,d], label='ground truth'); plt.plot(x, obs[idx,d], label='observed (with drops)'); plt.plot(x, recon[idx,d], label='synthesized'); plt.legend(); plt.xlabel('time'); plt.ylabel(f'feature {d}'); p=out_png.replace('.png', f'_f{d}.png'); plt.tight_layout(); plt.savefig(p, dpi=200); plt.close()
def plot_mask_heatmap(mask, out_png):
    plt.figure(); plt.imshow(mask.T, aspect='auto', origin='lower'); plt.xlabel('time'); plt.ylabel('feature'); plt.tight_layout(); plt.savefig(out_png, dpi=200); plt.close()
def plot_mae_bars(mae_per_feat, out_png):
    plt.figure(); x=np.arange(len(mae_per_feat)); plt.bar(x, mae_per_feat); plt.xlabel('feature index'); plt.ylabel('MAE'); plt.tight_layout(); plt.savefig(out_png, dpi=200); plt.close()
