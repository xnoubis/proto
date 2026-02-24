# Rosetta Terrain Experiments

## Quick Start (30 seconds)

```bash
# Demo with built-in text
python experiments.py quick

# Your documents
python experiments.py ingest ./my_docs/
python experiments.py run 100
python experiments.py meaning "your query"

# Interactive exploration
python experiments.py explore
```

## Real Embeddings (Better Results)

```bash
pip install sentence-transformers --break-system-packages
```

Without this, uses pseudo-embeddings (works but less semantic).

---

## Commands

| Command | What it does |
|---------|--------------|
| `quick` | Demo with built-in philosophical text |
| `ingest <folder>` | Load .txt/.md/.py files as terrain |
| `ingest-text "..."` | Load raw text string |
| `run <N>` | Navigate N steps, show snaps |
| `explore` | Interactive step-by-step mode |
| `meaning "query"` | Semantic search through terrain |
| `snaps` | Show crystallization moments |
| `coverage` | Test how much terrain gets explored |
| `reset` | Clear saved state |

---

## Interactive Mode Commands

```
s / step      Take one step
r N / run N   Run N steps
w / where     Show current position
n / neighbors Show adjacent nodes
m QUERY       Semantic search
snaps         Show snap events
path          Show recent path
q / quit      Exit
```

---

## Experiments to Try

### 1. Your Documents
```bash
python experiments.py ingest ~/Documents/essays/
python experiments.py run 200
python experiments.py meaning "what am I searching for"
```

### 2. Code as Terrain
```bash
python experiments.py ingest ./my_project/
python experiments.py explore
# Then: m "error handling"
```

### 3. Conversation Export
```bash
# Export Claude conversation as .txt
python experiments.py ingest ./exports/
python experiments.py meaning "key insight"
```

### 4. Compare Two Corpora
```bash
python experiments.py ingest ./corpus_a/
python experiments.py coverage
python experiments.py reset
python experiments.py ingest ./corpus_b/
python experiments.py coverage
```

---

## What Happens

1. **Ingest**: Text → chunks → embeddings → k-NN graph (terrain)
2. **Navigate**: Dragon Curve fractal guides exploration
3. **Hue**: Visited nodes get "bruised", decays over time
4. **Snap**: Entropy drops suddenly = crystallization moment
5. **Meaning**: Your query → embedding → similarity search

---

## The Riddle

*Find the key while being the key unknowingly.*

Your trajectory through the terrain accumulates into a shadow.
When that shadow aligns with a lock (pattern in structure),
meaning crystallizes. The agent didn't put the meaning there.
The agent didn't know it was the key. But through action, both become true.

---

## Files

- `experiments.py` - Main CLI
- `.rosetta_state.pkl` - Saved engine state (auto-created)
