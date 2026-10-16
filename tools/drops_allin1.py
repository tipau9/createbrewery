"""Suggests drops for a folder of songs with the All-In-One music structure analyzer (allin1).

    python drops_allin1.py <folder> [--compare]

Analyses every MP3 in <folder> (results cached as JSON in <folder>/.allin1), then writes
<folder>/drops-auto.txt in the drops.txt format DropTrainer reads. A drop is where a chorus
starts after a part that is not one, moved onto the nearest downbeat. Human-checked drops stay
in drops.txt; with --compare the suggestions are scored against them (same rules as DropTrainer).

Runs in the WSL env ~/allin1/env (allin1 needs NATTEN, which has no Windows build).
"""
import json
import sys
from pathlib import Path

# DropTrainer.EARLY / LATE: a found drop counts this close to a marked one.
EARLY, LATE = 1.5, 2.5
DROP_LABELS = {"chorus"}


def analyse(folder: Path) -> dict:
    cache = folder / ".allin1"
    cache.mkdir(exist_ok=True)
    songs = sorted(folder.glob("*.mp3"))
    todo = [s for s in songs if not (cache / (s.stem + ".json")).exists()]
    if todo:
        import allin1  # only when there is something to analyse
        print(f"Analysiere {len(todo)} von {len(songs)} Songs ...", flush=True)
        for s in todo:
            try:
                allin1.analyze(s, out_dir=cache, keep_byproducts=False)
            except Exception as e:  # one broken file must not stop the rest
                print(f"  {s.name}: {e}", flush=True)
    results = {}
    for s in songs:
        f = cache / (s.stem + ".json")
        if f.exists():
            results[s.name] = json.loads(f.read_text(encoding="utf-8"))
    return results


def drops(result: dict) -> list:
    found, prev = [], None
    downbeats = result.get("downbeats") or []
    for seg in result.get("segments", []):
        label = seg["label"]
        if label in DROP_LABELS and prev not in DROP_LABELS:
            t = seg["start"]
            if downbeats:
                t = min(downbeats, key=lambda d: abs(d - t))
            found.append(t)
        prev = label
    return found


def clock(t: float) -> str:
    return f"{int(t // 60)}:{t % 60:04.1f}"


def read_labels(file: Path) -> dict:
    """The checked lines of drops.txt (DropTrainer.readLabels): name -> drop times."""
    labels = {}
    if not file.exists():
        return labels
    for raw in file.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("?"):
            continue
        colon = line.lower().rfind(".mp3:")
        if colon < 0:
            continue
        times = []
        for t in line[colon + 5:].split(","):
            t = t.strip()
            if t and t != "-":
                m, _, s = t.rpartition(":")
                times.append((int(m) * 60 if m else 0) + float(s))
        labels[line[:colon + 4].strip()] = times
    return labels


def match(marked: list, found: list) -> tuple:
    left, hits = list(found), 0
    for m in marked:
        for f in left:
            if m - EARLY <= f <= m + LATE:
                left.remove(f)
                hits += 1
                break
    return hits, len(left), len(marked) - hits


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    folder = Path(sys.argv[1])
    results = analyse(folder)
    suggested = {name: drops(r) for name, r in results.items()}
    lines = [f"{name}: {', '.join(clock(t) for t in ts) if ts else '-'}" for name, ts in suggested.items()]
    (folder / "drops-auto.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(lines)} Songs in {folder / 'drops-auto.txt'}")

    if "--compare" in sys.argv:
        labels = read_labels(folder / "drops.txt")
        hits = wrong = missed = 0
        for name, marked in labels.items():
            if name not in suggested:
                continue
            h, w, m = match(marked, suggested[name])
            hits, wrong, missed = hits + h, wrong + w, missed + m
            print(f"  {name:45.45} markiert {', '.join(map(clock, marked)) or '-':22} allin1 {', '.join(map(clock, suggested[name])) or '-'}")
        f1 = 2 * hits / (2 * hits + wrong + missed) if hits else 0
        print(f"allin1 gegen drops.txt: {100 * f1:.0f}% ({hits} richtig, {wrong} falsch, {missed} verpasst)")


if __name__ == "__main__":
    main()
