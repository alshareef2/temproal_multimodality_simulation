#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Report generator for multimodal synchronization experiments (Appendix assets).

Scans:
  EXP_ROOT/
    exp_A/            # six experiment folders (any names)
      seed_000/       # ~20 seeds (any names)
        sync.csv      # system-level metrics over time
        t1.csv        # per-modality metrics over time
        t2.csv
        t3.csv
        t4.csv
    exp_B/
    ...

Outputs to --out:
  summaries/
    overall_summary.csv                  # exp-level aggregates (mean±std)
    latency_percentiles.csv              # med/p90/p99 per exp
    drops_summary.csv                    # drop totals & rates per exp
    per_modality_summary.csv             # t1..t4 aggregates per exp
  latex/
    table_overall.tex
    table_latency.tex
    table_drops.tex
    table_per_modality.tex
  figures/
    exp_<name>__queue_time.png
    exp_<name>__throughput_time.png
    exp_<name>__latency_hist.png
    exp_<name>__latency_box.png
    exp_<name>__drops_time.png

Assumptions / columns:
  sync.csv header (exact names, case sensitive):
    "Simulation time","Total waiting time","Total job waiting",
    "Average queue size","Job arrival rate","Throughput",
    "Job from queue","Job has waited for"
  tX.csv header:
    "Simulation time","Job arrival rate","Throughput","Total jobs lost"

Robustness:
  - Missing/blank cells are gracefully ignored.
  - Seeds/exps naming is free-form; presence of files determines parsing.
  - Latency "Job has waited for" may be sparse; we aggregate all non-empty samples.

Usage:
  python report_gen.py --root /path/to/EXP_ROOT --out ./appendix_assets
"""

import argparse
import os
import glob
import math
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path
from textwrap import dedent

# ----------------------------- I/O helpers -----------------------------

SYNC_REQUIRED = [
    "Simulation time", " Total waiting time", " Total job waiting",
    " Average queue size", " Job arrival rate", " Throughput",
    " Job from queue", " Job has waited for"
]
TX_REQUIRED = ["Simulation time", " Job arrival rate", " Throughput", " Total jobs lost"]

def read_csv_safe(path, required_cols):
    df = pd.read_csv(path)
    # Ensure required columns exist (some may be empty but present).
    missing = [c for c in required_cols if c not in df.columns]
    if missing:
        raise ValueError(f"Missing columns {missing} in {path}")
    # Coerce numeric columns where possible
    for c in df.columns:
        if c == " Job has waited for":
            # can be empty strings; keep as numeric where possible
            df[c] = pd.to_numeric(df[c], errors="coerce")
        elif c != "Simulation time":
            df[c] = pd.to_numeric(df[c], errors="coerce")
    # Simulation time numeric
    df["Simulation time"] = pd.to_numeric(df["Simulation time"], errors="coerce")
    df = df.dropna(subset=["Simulation time"])
    return df

def find_experiments(root):
    exps = []
    for p in sorted(Path(root).glob("*")):
        if p.is_dir():
            # treat any non-empty dir with at least one seed folder as experiment
            if any(sp.is_dir() for sp in p.iterdir()):
                exps.append(p)
    return exps

def find_seed_dirs(exp_dir):
    return [p for p in sorted(Path(exp_dir).glob("*")) if p.is_dir()]

# ------------------------ aggregation primitives -----------------------

def summarize_time_series_curves(seed_sync_dfs, xcol="Simulation time",
                                 ycols=(" Average queue size"," Throughput")):
    """
    Align by time via union of unique times across seeds (nearest-forward fill),
    then compute mean and std per time grid for each y in ycols.
    """
    # Build common time grid: union of all times, then sort & optionally thin
    times = np.unique(np.concatenate([df[xcol].values for df in seed_sync_dfs if len(df)>0]))
    times = np.array(sorted(times))
    # For stability, keep at most 1000 points
    if len(times) > 1000:
        idx = np.linspace(0, len(times)-1, 1000).astype(int)
        times = times[idx]

    out = { "time": times }
    for y in ycols:
        mat = []
        for df in seed_sync_dfs:
            if y not in df.columns or len(df)==0: 
                continue
            # reindex to common grid via interpolation on time
            s = df[[xcol, y]].dropna()
            if len(s)==0:
                continue
            si = np.interp(times, s[xcol].values, s[y].values, left=np.nan, right=np.nan)
            mat.append(si)
        if len(mat)==0:
            mean = np.full_like(times, np.nan, dtype=float)
            std  = np.full_like(times, np.nan, dtype=float)
        else:
            mat = np.stack(mat, axis=0)  # seeds x times
            mean = np.nanmean(mat, axis=0)
            std  = np.nanstd(mat, axis=0, ddof=1)
        out[f"{y}__mean"] = mean
        out[f"{y}__std"]  = std
    return pd.DataFrame(out)

def summarize_latency(seed_sync_dfs):
    """Aggregate all 'Job has waited for' samples across seeds; compute p50/p90/p99."""
    waits = []
    for df in seed_sync_dfs:
        if " Job has waited for" in df.columns:
            waits.append(df[" Job has waited for"].dropna().values)
    if not waits:
        return dict(median=np.nan, p90=np.nan, p99=np.nan)
    w = np.concatenate(waits) if len(waits)>0 else np.array([])
    if w.size == 0:
        return dict(median=np.nan, p90=np.nan, p99=np.nan)
    return dict(median=np.nanmedian(w),
                p90=np.nanpercentile(w, 90),
                p99=np.nanpercentile(w, 99))

def summarize_drops(seed_tx_dfs):
    """
    From t*.csv files per seed (one per modality), compute:
    - final Total jobs lost per modality (last row)
    - drop rate = (final lost) / (final time)  [approx per time unit]
    Returns aggregates (mean±std across seeds) per modality and overall.
    """
    per_modality = {}  # modality -> list of (final_lost, final_time, final_throughput)
    for seed in seed_tx_dfs:
        for mod_name, df in seed.items():
            if len(df)==0: 
                continue
            tlast = df["Simulation time"].max()
            last = df.loc[df["Simulation time"].idxmax()]
            lost = float(last.get(" Total jobs lost", np.nan))
            thr  = float(last.get(" Throughput", np.nan))
            per_modality.setdefault(mod_name, []).append((lost, tlast, thr))

    rows = []
    for mod, items in per_modality.items():
        losts = np.array([x[0] for x in items], dtype=float)
        times = np.array([x[1] for x in items], dtype=float)
        thrs  = np.array([x[2] for x in items], dtype=float)
        rates = losts / np.where(times>0, times, np.nan)
        rows.append({
            "modality": mod,
            "lost_mean": np.nanmean(losts), "lost_std": np.nanstd(losts, ddof=1),
            "drop_rate_mean": np.nanmean(rates), "drop_rate_std": np.nanstd(rates, ddof=1),
            "throughput_last_mean": np.nanmean(thrs), "throughput_last_std": np.nanstd(thrs, ddof=1)
        })
    drops_df = pd.DataFrame(rows).sort_values("modality")
    # Overall (sum across modalities averaged over seeds approximately)
    overall = {
        "modality": "overall",
        "lost_mean": drops_df["lost_mean"].sum(),
        "lost_std": np.sqrt((drops_df["lost_std"]**2).sum()),
        "drop_rate_mean": drops_df["drop_rate_mean"].sum(),
        "drop_rate_std": np.sqrt((drops_df["drop_rate_std"]**2).sum()),
        "throughput_last_mean": drops_df["throughput_last_mean"].sum(),
        "throughput_last_std": np.sqrt((drops_df["throughput_last_std"]**2).sum()),
    }
    drops_df = pd.concat([drops_df, pd.DataFrame([overall])], ignore_index=True)
    return drops_df

def mean_std_across_seeds(seed_sync_dfs):
    """Compute overall mean±std of key scalars over time by averaging per-seed time averages."""
    scalars = []
    for df in seed_sync_dfs:
        if len(df)==0: 
            continue
        # time-averaged metrics per seed
        scalars.append({
            "avg_queue_mean": df[" Average queue size"].dropna().mean(),
            "throughput_mean": df[" Throughput"].dropna().mean(),
            "arrival_rate_mean": df[" Job arrival rate"].dropna().mean(),
            "total_waiting_time_last": df[" Total waiting time"].dropna().max(),
            "total_job_waiting_last": df[" Total job waiting"].dropna().max()
        })
    if not scalars:
        return pd.Series({k: np.nan for k in ["avg_queue_mean","avg_queue_std","throughput_mean","throughput_std",
                                              "arrival_rate_mean","arrival_rate_std",
                                              "total_waiting_time_last_mean","total_waiting_time_last_std",
                                              "total_job_waiting_last_mean","total_job_waiting_last_std"]})
    S = pd.DataFrame(scalars)
    out = {
        "avg_queue_mean": S["avg_queue_mean"].mean(),
        "avg_queue_std":  S["avg_queue_mean"].std(ddof=1),
        "throughput_mean": S["throughput_mean"].mean(),
        "throughput_std":  S["throughput_mean"].std(ddof=1),
        "arrival_rate_mean": S["arrival_rate_mean"].mean(),
        "arrival_rate_std":  S["arrival_rate_mean"].std(ddof=1),
        "total_waiting_time_last_mean": S["total_waiting_time_last"].mean(),
        "total_waiting_time_last_std":  S["total_waiting_time_last"].std(ddof=1),
        "total_job_waiting_last_mean": S["total_job_waiting_last"].mean(),
        "total_job_waiting_last_std":  S["total_job_waiting_last"].std(ddof=1),
    }
    return pd.Series(out)

# ------------------------------ plotting ------------------------------

def plot_mean_with_band(df_meanstd, x, y_mean, y_std, title, xlabel, ylabel, out_path):
    plt.figure(figsize=(7,4))
    xm = df_meanstd[x].values
    ym = df_meanstd[y_mean].values
    ys = df_meanstd[y_std].values
    plt.plot(xm, ym, label="mean")
    plt.fill_between(xm, ym-ys, ym+ys, alpha=0.2, label="±1 std")
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    plt.title(title)
    plt.legend(frameon=False)
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(out_path, dpi=300)
    plt.close()

def plot_hist(values, title, xlabel, out_path):
    plt.figure(figsize=(7,4))
    v = np.asarray(values)
    v = v[np.isfinite(v)]
    if v.size == 0:
        # Empty plot placeholder
        plt.text(0.5,0.5,"No data", ha="center", va="center")
    else:
        plt.hist(v, bins=50)
    plt.xlabel(xlabel)
    plt.ylabel("Count")
    plt.title(title)
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(out_path, dpi=300)
    plt.close()

def plot_box(values_by_seed, title, ylabel, out_path):
    plt.figure(figsize=(6,4))
    # values_by_seed: list of 1D arrays (one per seed)
    data = [np.asarray(v)[np.isfinite(v)] for v in values_by_seed if len(v)>0]
    if not data:
        plt.text(0.5,0.5,"No data", ha="center", va="center")
    else:
        plt.boxplot(data, vert=True, showfliers=False)
    plt.ylabel(ylabel)
    plt.title(title)
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(out_path, dpi=300)
    plt.close()

# ------------------------------ LaTeX ---------------------------------

def fmt_pm(mean, std, prec=3):
    if math.isnan(mean):
        return "--"
    if math.isnan(std) or std == 0:
        return f"{mean:.{prec}f}"
    return f"{mean:.{prec}f} ± {std:.{prec}f}"

def write_latex_tables(out_dir, overall_df, latency_df, drops_df, per_mod_df):
    latex_dir = Path(out_dir, "latex"); latex_dir.mkdir(parents=True, exist_ok=True)

    # Overall table
    lines = [r"\begin{table}[h]",
             r"\centering",
             r"\caption{Overall synchronization and throughput (mean ± std across seeds).}",
             r"\label{tab:overall_sync}",
             r"\begin{tabular}{lcccc}",
             r"\toprule",
             r"Experiment & Avg. Queue & Throughput & Arrival Rate & Total Waiting Time \\",
             r"\midrule"]
    for _, r in overall_df.iterrows():
        lines.append(f"{r['experiment']} & {fmt_pm(r['avg_queue_mean'], r['avg_queue_std'])} "
                     f"& {fmt_pm(r['throughput_mean'], r['throughput_std'])} "
                     f"& {fmt_pm(r['arrival_rate_mean'], r['arrival_rate_std'])} "
                     f"& {fmt_pm(r['total_waiting_time_last_mean'], r['total_waiting_time_last_std'])} \\\\")
    lines += [r"\bottomrule", r"\end{tabular}", r"\end{table}"]
    Path(latex_dir, "table_overall.tex").write_text("\n".join(lines))

    # Latency table
    lines = [r"\begin{table}[h]",
             r"\centering",
             r"\caption{Latency per message (median / p90 / p99) in time units.}",
             r"\label{tab:latency}",
             r"\begin{tabular}{lccc}",
             r"\toprule",
             r"Experiment & Median & p90 & p99 \\",
             r"\midrule"]
    for _, r in latency_df.iterrows():
        lines.append(f"{r['experiment']} & {r['median']:.3f} & {r['p90']:.3f} & {r['p99']:.3f} \\\\")
    lines += [r"\bottomrule", r"\end{tabular}", r"\end{table}"]
    Path(latex_dir, "table_latency.tex").write_text("\n".join(lines))

    # Drops table
    lines = [r"\begin{table}[h]",
             r"\centering",
             r"\caption{Drop statistics per modality (mean ± std across seeds). Drop rate in $1/$time-unit.}",
             r"\label{tab:drops}",
             r"\begin{tabular}{lccc}",
             r"\toprule",
             r"Modality & Total Lost & Drop Rate & Last Throughput \\",
             r"\midrule"]
    for _, r in drops_df.iterrows():
        lines.append(f"{r['experiment']}:{r['modality']} & "
                     f"{fmt_pm(r['lost_mean'], r['lost_std'])} & "
                     f"{fmt_pm(r['drop_rate_mean'], r['drop_rate_std'])} & "
                     f"{fmt_pm(r['throughput_last_mean'], r['throughput_last_std'])} \\\\")
    lines += [r"\bottomrule", r"\end{tabular}", r"\end{table}"]
    Path(latex_dir, "table_drops.tex").write_text("\n".join(lines))

    # Per-modality summary (collapse across exps + mods)
    per_mod_df.to_csv(Path(out_dir, "summaries", "per_modality_summary.csv"), index=False)

# ------------------------------- driver --------------------------------

def main(root, out):
    root = Path(root); out = Path(out)
    (out / "summaries").mkdir(parents=True, exist_ok=True)
    (out / "figures").mkdir(parents=True, exist_ok=True)

    exps = find_experiments(root)
    overall_rows, latency_rows, drops_rows = [], [], []
    per_mod_rows = []

    for exp_dir in exps:
        exp_name = exp_dir.name
        seed_dirs = find_seed_dirs(exp_dir)
        seed_sync_dfs, seed_tx_dfs = [], []

        # parse each seed
        latency_vals_by_seed = []
        for sd in seed_dirs:
            sync_path = sd / "sync.csv"
            if not sync_path.exists():
                continue
            try:
                sync_df = read_csv_safe(sync_path, SYNC_REQUIRED)
            except Exception as e:
                print(f"[WARN] {e}")
                continue
            seed_sync_dfs.append(sync_df)

            # collect modality files t1..t4
            mod_files = {}
            for tpath in sorted(sd.glob("t*.csv")):
                mod_name = tpath.stem  # t1, t2, ...
                try:
                    tdf = read_csv_safe(tpath, TX_REQUIRED)
                except Exception as e:
                    print(f"[WARN] {e}")
                    tdf = pd.DataFrame(columns=TX_REQUIRED)
                mod_files[mod_name] = tdf
                # for per-mod summary later
                if len(tdf):
                    per_mod_rows.append({
                        "experiment": exp_name,
                        "seed": sd.name,
                        "modality": mod_name,
                        "time_last": tdf["Simulation time"].max(),
                        "throughput_mean": tdf[" Throughput"].dropna().mean(),
                        "drop_total_last": tdf[" Total jobs lost"].dropna().max()
                    })
            seed_tx_dfs.append(mod_files)

            # latency samples for this seed
            waits = sync_df[" Job has waited for"].dropna().values
            if waits.size:
                latency_vals_by_seed.append(waits)

        if not seed_sync_dfs:
            print(f"[INFO] No valid seeds in {exp_name}, skipping.")
            continue

        # time-series mean±std for queue and throughput
        curves = summarize_time_series_curves(seed_sync_dfs)
        # plots
        plot_mean_with_band(curves, "time", " Average queue size__mean", " Average queue size__std",
                            f"{exp_name}: Average Queue Size over Time",
                            "Simulation Time (time units)", "Average Queue Size (elements)",
                            out / "figures" / f"exp_{exp_name}__queue_time.png")
        plot_mean_with_band(curves, "time", " Throughput__mean", " Throughput__std",
                            f"{exp_name}: Throughput over Time",
                            "Simulation Time (time units)", "Throughput (jobs / time-unit)",
                            out / "figures" / f"exp_{exp_name}__throughput_time.png")

        # latency summaries + plots
        lat_summ = summarize_latency(seed_sync_dfs)
        latency_rows.append({"experiment": exp_name, **lat_summ})
        # Hist & box
        all_waits = np.concatenate(latency_vals_by_seed) if latency_vals_by_seed else np.array([])
        plot_hist(all_waits, f"{exp_name}: Latency Histogram", "Latency (time units)",
                  out / "figures" / f"exp_{exp_name}__latency_hist.png")
        plot_box(latency_vals_by_seed, f"{exp_name}: Latency Across Seeds", "Latency (time units)",
                 out / "figures" / f"exp_{exp_name}__latency_box.png")

        # drops summary (per modality + overall)
        drops_df = summarize_drops(seed_tx_dfs)
        drops_df.insert(0, "experiment", exp_name)
        # Save per-exp drops details for appendix
        drops_df.to_csv(out / "summaries" / f"{exp_name}__drops_summary.csv", index=False)
        drops_rows.append(drops_df)

        # overall mean/std scalars
        ov = mean_std_across_seeds(seed_sync_dfs).to_dict()
        overall_rows.append({"experiment": exp_name, **ov})

    # combine and save global summaries
    overall_df = pd.DataFrame(overall_rows).sort_values("experiment")
    latency_df = pd.DataFrame(latency_rows).sort_values("experiment")
    drops_all = pd.concat(drops_rows, ignore_index=True) if drops_rows else pd.DataFrame()
    per_mod_df = pd.DataFrame(per_mod_rows)

    (out / "summaries" / "overall_summary.csv").write_text(overall_df.to_csv(index=False))
    (out / "summaries" / "latency_percentiles.csv").write_text(latency_df.to_csv(index=False))
    if not drops_all.empty:
        (out / "summaries" / "drops_summary.csv").write_text(drops_all.to_csv(index=False))

    write_latex_tables(out, overall_df, latency_df, drops_all, per_mod_df)

    # Appendix LaTeX snippet
    latex_app = dedent(r"""
    %%%%%%%%%%%%%%%%%%%%%%%%% Appendix: Reporting %%%%%%%%%%%%%%%%%%%%%%%%%
    \section*{Appendix: Extended Synchronization and Reconstruction Results}
    \subsection*{A. Summary Tables}
    \input{appendix_assets/latex/table_overall.tex}
    \input{appendix_assets/latex/table_latency.tex}
    \input{appendix_assets/latex/table_drops.tex}

    \subsection*{B. Figures (per experiment)}
    % Replace EXP_A with your experiment folder names
    \begin{figure}[h]
      \centering
      \includegraphics[width=0.48\textwidth]{appendix_assets/figures/exp_EXP_A__queue_time.png}
      \includegraphics[width=0.48\textwidth]{appendix_assets/figures/exp_EXP_A__throughput_time.png}
      \caption{Average Queue Size and Throughput over time (mean ± std across seeds).}
    \end{figure}
    \begin{figure}[h]
      \centering
      \includegraphics[width=0.48\textwidth]{appendix_assets/figures/exp_EXP_A__latency_hist.png}
      \includegraphics[width=0.48\textwidth]{appendix_assets/figures/exp_EXP_A__latency_box.png}
      \caption{Latency distribution and variability across seeds.}
    \end{figure}
    """).strip()
    Path(out, "latex", "APPENDIX_SNIPPET.tex").write_text(latex_app)

    print(f"[OK] Wrote summaries to: {out.resolve()}")
    print(" - CSV: summaries/*.csv")
    print(" - LaTeX tables: latex/*.tex (\\input into your appendix)")
    print(" - Figures: figures/*.png")
    print(" - Appendix snippet: latex/APPENDIX_SNIPPET.tex")
    print("Tip: in LaTeX, set \\graphicspath{{appendix_assets/figures/}} and \\input the tables.")

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True, help="Path to EXP_ROOT")
    ap.add_argument("--out", required=True, help="Output folder for appendix assets")
    args = ap.parse_args()
    main(args.root, args.out)
