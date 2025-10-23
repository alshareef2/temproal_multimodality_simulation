import os, json, argparse, numpy as np, torch, torch.optim as optim
from torch.utils.data import DataLoader, random_split
from data import load_csv_series, zscore_normalize_nan_safe, WindowedSeries
from model import TemporalDDPM
def main(config='config.json'):
    cfg=json.load(open(config))
    ts, X_filled, Mnat, feats=load_csv_series(cfg['data']['csv'], cfg['data']['timestamp_col'], cfg['data']['feature_cols'])
    if cfg['data'].get('normalize', True):
        Xn, mean, std = zscore_normalize_nan_safe(X_filled, Mnat); norm_stats={'mean':mean.tolist(),'std':std.tolist()}
    else: Xn=X_filled; norm_stats=None
    T=cfg['data']['window_len']; stride=cfg['data']['stride']
    ds=WindowedSeries(Xn, Mnat, T=T, stride=stride, drop_prob=cfg['train']['mask_drop_prob'], train=True)
    val_len=max(1,int(len(ds)*cfg['train']['val_split'])); train_len=len(ds)-val_len
    from torch.utils.data import random_split
    ds_train, ds_val=random_split(ds,[train_len,val_len],generator=torch.Generator().manual_seed(0))
    dl_train=DataLoader(ds_train,batch_size=cfg['train']['batch_size'],shuffle=True)
    dl_val=DataLoader(ds_val,batch_size=cfg['train']['batch_size'],shuffle=False)
    device=torch.device(cfg['train']['device'] if torch.cuda.is_available() else 'cpu')
    D=Xn.shape[1]
    model=TemporalDDPM(data_dim=D, hidden=cfg['model']['hidden'], depth=cfg['model']['depth'], timesteps=cfg['model']['timesteps'], schedule=cfg['model']['schedule']).to(device=device, dtype=torch.float32)
    opt=optim.AdamW(model.parameters(), lr=cfg['train']['lr'])
    os.makedirs(cfg['train']['save_dir'], exist_ok=True)
    step=0
    for epoch in range(cfg['train']['epochs']):
        model.train()
        for batch in dl_train:
            x_full=batch['x_full'].to(device,dtype=torch.float32); m_nat=batch['mask_nat'].to(device,dtype=torch.float32)
            x_obs=batch['x_obs'].to(device,dtype=torch.float32); m_obs=batch['mask_obs'].to(device,dtype=torch.float32)
            cond=torch.cat([x_obs,m_obs],dim=-1).to(torch.float32); miss_train=(m_nat*(1.0-m_obs))
            t=torch.randint(0, model.timesteps, (x_full.size(0),), device=device, dtype=torch.long)
            loss=model.p_losses(x_full,t,cond,mask_obs=m_obs,mask_natural=m_nat,mask_loss=miss_train)
            opt.zero_grad(); loss.backward(); opt.step()
            if step % cfg['train']['log_every']==0: print(f"epoch {epoch} step {step} loss {loss.item():.4f}")
            step+=1
        model.eval()
        with torch.no_grad():
            vb=next(iter(dl_val)); x_full=vb['x_full'].to(device,dtype=torch.float32); m_nat=vb['mask_nat'].to(device,dtype=torch.float32)
            x_obs=vb['x_obs'].to(device,dtype=torch.float32); m_obs=vb['mask_obs'].to(device,dtype=torch.float32)
            cond=torch.cat([x_obs,m_obs],dim=-1).to(torch.float32); x_hat=model.ddim_impute(x_obs,1.0-m_obs,cond,steps=cfg['model']['sampler_steps'])
            val_mae=(torch.sum(torch.abs(x_hat-x_full)*(m_nat*(1.0-m_obs))) / (torch.sum(m_nat*(1.0-m_obs))+1e-8)).item()
            print(f"[val] epoch {epoch} MAE@missing {val_mae:.4f}")
        torch.save({'state_dict':model.state_dict(),'config':cfg,'features':feats,'norm':norm_stats}, os.path.join(cfg['train']['save_dir'],'ckpt.pt'))
if __name__=='__main__':
    import argparse; ap=argparse.ArgumentParser(); ap.add_argument('--config',type=str,default='config.json'); args=ap.parse_args(); main(args.config)
