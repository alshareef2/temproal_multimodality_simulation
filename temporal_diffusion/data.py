# data.py
import os
import warnings
import numpy as np
import pandas as pd
import torch
from torch.utils.data import Dataset


def _parse_timestamps(series: pd.Series) -> pd.Series:
    """
    Parse timestamps robustly without throwing the 'Could not infer format' warning.
    Tries (1) with seconds, (2) without seconds, then (3) generic parse (silenced).
    """
    # 1) with seconds, e.g. 05/22/2015 3:00:00 PM
    ts = pd.to_datetime(series, format="%m/%d/%Y %I:%M:%S %p", errors="coerce")

    # 2) without seconds, e.g. 05/22/2015 3:00 PM
    if ts.isna().any():
        ts2 = pd.to_datetime(series, format="%m/%d/%Y %I:%M %p", errors="coerce")
        ts = ts.fillna(ts2)

    # 3) last resort: generic parse, silence warnings
    if ts.isna().any():
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", category=UserWarning)
            ts3 = pd.to_datetime(series, errors="coerce", infer_datetime_format=False)
        ts = ts.fillna(ts3)

    return ts


def load_csv_series(path, timestamp_col='timestamp', feature_cols=None):
    """
    Loads a CSV time series, coercing non-numerics to NaN, building a natural mask,
    and zero-filling NaNs (mask carries the information about true missingness).

    Returns:
      ts:        np.ndarray of datetimes (may contain NaT)
      X_filled:  float32 array [N, D] with zeros where natural NaN
      nat_mask:  float32 array [N, D], 1 where CSV had a finite value, else 0
      feature_cols: list[str] of feature column names actually used
    """
    df = pd.read_csv(path)

    # If feature_cols not given, use all columns except timestamp
    if feature_cols is None or len(feature_cols) == 0:
        feature_cols = [c for c in df.columns if c != timestamp_col]

    # Coerce features to numeric; non-numeric -> NaN
    for c in feature_cols:
        df[c] = pd.to_numeric(df[c], errors='coerce')

    # Robust, warning-free timestamp parsing
    ts = _parse_timestamps(df[timestamp_col])

    X = df[feature_cols].to_numpy(dtype=np.float32)   # may include NaNs
    nat_mask = np.isfinite(X).astype(np.float32)      # 1 where true value exists
    X_filled = np.nan_to_num(X, nan=0.0)              # safe carrier (mask keeps info)

    return ts.to_numpy(), X_filled, nat_mask, feature_cols


def zscore_normalize_nan_safe(X, mask, eps=1e-6):
    """
    Z-score normalize using only naturally present entries (mask==1).
    Keeps zeros where entries are naturally missing.
    """
    count = np.clip(mask.sum(axis=0, keepdims=True), 1.0, None)
    mean = (X * mask).sum(axis=0, keepdims=True) / count
    var  = ((X - mean) ** 2 * mask).sum(axis=0, keepdims=True) / count
    std  = np.sqrt(var) + eps

    Xn = (X - mean) / std
    Xn = Xn * mask  # keep zeros where naturally missing

    return Xn.astype(np.float32), mean.astype(np.float32), std.astype(np.float32)


class WindowedSeries(Dataset):
    """
    Produces overlapping windows from a (possibly long) time series, with:
      - X_filled: zero-filled features where natural NaN
      - nat_mask: mask of naturally present entries (1 = present in CSV)
      - During training: apply simulated Bernoulli drops to create observed mask
    Returns per item:
      {
        'x_full':   [T, D] zero-filled values,
        'mask_nat': [T, D] 1 where CSV had a value,
        'x_obs':    [T, D] observed values after sim-drop (zeros elsewhere),
        'mask_obs': [T, D] observed mask = mask_nat ∧ sim_mask
      }
    """
    def __init__(self, X_filled, nat_mask, T=256, stride=64, drop_prob=0.2, train=True):
        self.X = X_filled.astype(np.float32)
        self.Mnat = nat_mask.astype(np.float32)
        self.T = int(T)
        self.stride = int(stride)
        self.drop_prob = float(drop_prob)
        self.train = train

        N = len(X_filled)
        if N < self.T:
            # Single window fallback for short sequences
            self.idxs = [(0, N)]
        else:
            self.idxs = [(s, s + self.T) for s in range(0, N - self.T + 1, self.stride)]

        if not self.idxs:
            raise ValueError("No windows available; check window_len/stride.")

    def __len__(self):
        return len(self.idxs)

    def __getitem__(self, i):
        s, e = self.idxs[i]

        x = self.X[s:e] if e <= len(self.X) else self.X[s:]
        m_nat = self.Mnat[s:e] if e <= len(self.Mnat) else self.Mnat[s:]

        if self.train:
            # Simulated availability (1=kept, 0=dropped)
            m_sim = (np.random.rand(*x.shape) > self.drop_prob).astype(np.float32)
        else:
            m_sim = np.ones_like(x, dtype=np.float32)

        m_obs = m_nat * m_sim
        x_obs = np.where(m_obs > 0, x, 0.0)

        return {
            'x_full':   torch.from_numpy(x.astype(np.float32)),
            'mask_nat': torch.from_numpy(m_nat.astype(np.float32)),
            'x_obs':    torch.from_numpy(x_obs.astype(np.float32)),
            'mask_obs': torch.from_numpy(m_obs.astype(np.float32)),
        }
