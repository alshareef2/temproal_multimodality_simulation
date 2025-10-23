import numpy as np
def masked_mae(a,b,m): num=np.sum(np.abs(a-b)*m); den=np.sum(m)+1e-8; return float(num/den)
def masked_mse(a,b,m): num=np.sum(((a-b)**2)*m); den=np.sum(m)+1e-8; return float(num/den)
