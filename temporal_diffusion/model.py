import math, torch, torch.nn as nn, torch.nn.functional as F
def sinusoidal_time_embed(t, dim):
    device=t.device; half=dim//2
    freqs=torch.exp(torch.arange(half, device=device, dtype=torch.float32)*(-math.log(10000.0)/max(half-1,1)))
    args=t[:,None].float()*freqs[None]; emb=torch.cat([torch.sin(args), torch.cos(args)], dim=-1)
    if dim%2==1: emb=torch.cat([emb, torch.zeros_like(emb[:,:1])], dim=-1)
    return emb
class ResidualBlock(nn.Module):
    def __init__(self, dim, cond_dim):
        super().__init__(); self.lin1=nn.Linear(dim,dim); self.lin2=nn.Linear(dim,dim); self.emb=nn.Linear(cond_dim,dim); self.norm=nn.LayerNorm(dim)
    def forward(self, x, t_emb, c_emb):
        h=self.lin1(self.norm(x))+self.emb(t_emb+c_emb); h=F.gelu(h); h=self.lin2(self.norm(h)); return x+h
class UNet1D(nn.Module):
    def __init__(self, data_dim, hidden=128, depth=4, cond_dim=128):
        super().__init__(); self.inp=nn.Linear(data_dim,hidden); self.blocks=nn.ModuleList([ResidualBlock(hidden,cond_dim) for _ in range(depth)]); self.out=nn.Linear(hidden,data_dim); self.tproj=nn.Linear(hidden,cond_dim)
    def forward(self, x, t, cond):
        t_emb=sinusoidal_time_embed(t, self.tproj.in_features); t_emb=self.tproj(t_emb); h=self.inp(x); t_b=t_emb[:,None,:].expand(h.size(0),h.size(1),-1); c_emb=cond; d=self.blocks[0].emb.in_features
        if c_emb.size(-1)!=d:
            if c_emb.size(-1)<d:
                pad=torch.zeros(c_emb.size(0),c_emb.size(1),d-c_emb.size(-1), device=c_emb.device, dtype=c_emb.dtype); c_emb=torch.cat([c_emb,pad], dim=-1)
            else: c_emb=c_emb[...,:d]
        for blk in self.blocks: h=blk(h,t_b,c_emb)
        return self.out(h)
class TemporalDDPM(nn.Module):
    def __init__(self, data_dim, hidden=128, depth=4, timesteps=500, schedule='cosine'):
        super().__init__(); self.model=UNet1D(data_dim,hidden,depth,cond_dim=128); self.timesteps=timesteps; self.register_buffer('betas', self.make_betas(timesteps,schedule), persistent=False); alphas=1.0-self.betas; self.register_buffer('alphas_cumprod', torch.cumprod(alphas,dim=0), persistent=False)
    def make_betas(self, T, schedule):
        if schedule=='cosine':
            s=0.008; t=torch.linspace(0,T,T+1); f=torch.cos(((t/T)+s)/(1+s)*math.pi/2)**2; alphas_cum=f/f[0]; betas=1-(alphas_cum[1:]/alphas_cum[:-1]); return betas.clamp(1e-5,0.02)
        else: return torch.linspace(1e-4,0.02,T)
    def q_sample(self, x0, t, noise=None):
        if noise is None: noise=torch.randn_like(x0); a=self.alphas_cumprod[t][:,None,None]; return torch.sqrt(a)*x0+torch.sqrt(1-a)*noise, noise
    def p_losses(self, x0, t, cond, mask_obs, mask_natural=None, mask_loss=None):
        x_t, noise = self.q_sample(x0, t); pred=self.model(x_t,t,cond); miss=(1.0-mask_obs) if mask_loss is None else mask_loss; return ((pred-noise)**2 * miss).sum()/(miss.sum()+1e-8)
    @torch.no_grad()
    def ddim_impute(self, x_obs, mask_miss, cond, steps=50):
        B,T,D=x_obs.shape; x=torch.randn_like(x_obs); x=x*(mask_miss)+x_obs*(1-mask_miss); seq=torch.linspace(0,self.timesteps-1,steps).long().flip(0).to(x.device)
        for i,t in enumerate(seq):
            t_b=torch.full((B,), t.item(), device=x.device, dtype=torch.long); eps=self.model(x,t_b,cond); a_t=self.alphas_cumprod[t]
            x0_hat=(x - torch.sqrt(1-a_t)*eps)/torch.sqrt(a_t+1e-8); x0_hat=x0_hat*(mask_miss)+x_obs*(1-mask_miss)
            if i < len(seq)-1:
                t_next=seq[i+1]; a_next=self.alphas_cumprod[t_next]; x=torch.sqrt(a_next)*x0_hat + torch.sqrt(1-a_next)*eps
            else: x=x0_hat
            x=x*(mask_miss)+x_obs*(1-mask_miss)
        return x
