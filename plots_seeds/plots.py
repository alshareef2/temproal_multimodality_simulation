import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# ===================== user settings =====================
base_path = r"ExpResults"   # <-- change this to your root folder
y_candidates = ["Staleness", "Average queue size", "Avg queue size", "AverageQueueSize"]

SHOW_SEED_LINES = True       # gray thin lines
SHOW_MEAN_LINE  = True       # blue mean line
SHOW_STD_RIBBON = False      # shaded ±1σ ribbon around mean

MAX_GRID_POINTS = 2000       # common time grid size (per experiment)
GRID_MODE = "quantile"       # "quantile" (follows data density) or "linspace"

SEED_ALPHA = 0.6
SEED_LINEWIDTH = 0.35
MEAN_LINEWIDTH = 1.6
DPI = 300
# ========================================================


def find_col(df, candidates):
    """Return original column name matching any candidate (case/space-insensitive)."""
    norm = {c: c.strip().lower().replace(" ", "") for c in df.columns}
    for cand in candidates:
        target = cand.strip().lower().replace(" ", "")
        for orig, n in norm.items():
            if n == target:
                return orig
    # loose match fallback (contains)
    for cand in candidates:
        target = cand.strip().lower().replace(" ", "")
        for orig, n in norm.items():
            if target in n or n in target:
                return orig
    return None


def load_seed_series(file_path):
    """Load a single seed CSV → (t_sorted, y_sorted) as float numpy arrays."""
    df = pd.read_csv(file_path, skipinitialspace=True, encoding="utf-8-sig")
    df.columns = df.columns.str.strip()

    t_col = find_col(df, ["Simulation time"])
    y_col = find_col(df, y_candidates)
    if t_col is None or y_col is None:
        raise KeyError(f"Missing columns in {file_path}. Found: {list(df.columns)}")

    s = df[[t_col, y_col]].rename(columns={t_col: "t", y_col: "y"}).copy()
    s["t"] = pd.to_numeric(s["t"], errors="coerce")
    s["y"] = pd.to_numeric(s["y"], errors="coerce")
    s = s.dropna(subset=["t", "y"]).sort_values("t")

    if s.empty:
        raise ValueError(f"Empty after cleanup: {file_path}")

    # If there are duplicate times, average them to ensure strictly increasing t for interp
    s = s.groupby("t", as_index=False)["y"].mean().sort_values("t")
    t = s["t"].to_numpy()
    y = s["y"].to_numpy()
    # make strictly increasing (monotone), guard against any accidental non-increasing
    inc = np.diff(t) > 0
    if not np.all(inc):
        keep = np.r_[True, inc]  # keep first, drop non-increasing repeats
        t, y = t[keep], y[keep]
    if t.size < 2:
        raise ValueError(f"Need at least 2 time points to interpolate: {file_path}")
    return t, y


def build_common_grid(all_times, max_points=2000, mode="quantile"):
    """Build a common time grid from pooled times of all seeds."""
    all_times = np.asarray(all_times, dtype=float)
    all_times = all_times[np.isfinite(all_times)]
    if all_times.size == 0:
        return None
    tmin, tmax = float(np.min(all_times)), float(np.max(all_times))
    if tmax <= tmin:
        return None

    if mode == "quantile":
        # follow data density: pick quantiles of pooled times
        qs = np.linspace(0, 1, max_points)
        grid = np.quantile(all_times, qs)
        # ensure strictly increasing (may have flats if many duplicates)
        grid = np.unique(grid)
        if grid.size < 2:  # fallback to linspace
            grid = np.linspace(tmin, tmax, max_points)
    else:
        grid = np.linspace(tmin, tmax, max_points)

    return grid


def interpolate_to_grid(t, y, grid):
    """Interpolate (t,y) to 'grid' using linear interpolation (extrapolate by edge-hold)."""
    # numpy.interp extrapolates by holding endpoints (left/right)
    return np.interp(grid, t, y)


# ---------- main plotting ----------
exp_folders = sorted([d for d in os.listdir(base_path) if os.path.isdir(os.path.join(base_path, d))])

for i, exp in enumerate(exp_folders, 1):
    exp_path = os.path.join(base_path, exp)
    seed_dirs = sorted([d for d in os.listdir(exp_path) if os.path.isdir(os.path.join(exp_path, d))])

    seed_series = []  # list of (t, y)
    pooled_times = []

    # Load all seeds
    for sd in seed_dirs:
        fp = os.path.join(exp_path, sd, "sync.csv")
        if not os.path.exists(fp):
            continue
        try:
            t, y = load_seed_series(fp)
        except Exception as e:
            print(f"[skip] {fp}: {e}")
            continue
        seed_series.append((t, y))
        # down-sample the times we pool for grid construction to keep memory sane
        if t.size > 20000:
            pooled_times.append(t[::10])
        else:
            pooled_times.append(t)

    if not seed_series:
        print(f"[warn] No valid seeds for {exp}")
        # still emit an empty figure to keep filenames consistent
        plt.figure(figsize=(8, 5))
        plt.title(f"Experiment {i}: (no valid seeds)")
        plt.xlabel("Simulation time")
        plt.ylabel("Metric (staleness)")
        plt.tight_layout()
        plt.savefig(f"{exp}_seeds_plot.png", dpi=DPI)
        plt.close()
        continue

    pooled_times = np.concatenate(pooled_times)
    grid = build_common_grid(pooled_times, max_points=MAX_GRID_POINTS, mode=GRID_MODE)
    if grid is None:
        print(f"[warn] Could not build grid for {exp}"); continue

    # Interpolate each seed onto the common grid
    seed_on_grid = []
    for (t, y) in seed_series:
        y_grid = interpolate_to_grid(t, y, grid)
        seed_on_grid.append(y_grid)

    seed_on_grid = np.vstack(seed_on_grid)   # shape: [n_seeds, len(grid)]
    mean_curve = seed_on_grid.mean(axis=0)
    std_curve  = seed_on_grid.std(axis=0)

    # ---- plot ----
    fig, ax = plt.subplots(figsize=(8, 5))

    if SHOW_SEED_LINES:
        # Draw each seed using the grid (already downsampled), keeps visuals light
        for y_grid in seed_on_grid:
            ax.plot(grid, y_grid, color="0.6", alpha=SEED_ALPHA,
                    linewidth=SEED_LINEWIDTH, solid_capstyle="butt")

    if SHOW_STD_RIBBON:
        ax.fill_between(grid, mean_curve - std_curve, mean_curve + std_curve,
                        alpha=0.2, label="±1 σ")

    if SHOW_MEAN_LINE:
        ax.plot(grid, mean_curve, color="#0066ff",
                linewidth=MEAN_LINEWIDTH, alpha=1.0,
                solid_capstyle="round", label="Average")
        ax.legend(frameon=False, fontsize=9)

    ax.set_title(f"Experiment {i}: Simulation time vs mean")
    ax.set_xlabel("Simulation Time (time units)")
    ax.set_ylabel("Metric (staleness, number of enqueued inputs)")
    ax.grid(True, alpha=0.3)
    fig.tight_layout()
    fig.savefig(f"{exp}_seeds_plot.png", dpi=DPI)
    plt.close(fig)

print("Done.")
