#!/usr/bin/env python3
"""
ROSETTA EXPERIMENTS - Simple Pipelines
=======================================

QUICK START:
    pip install sentence-transformers --break-system-packages
    python experiments.py quick              # Demo with built-in text
    python experiments.py ingest ./docs/     # Ingest your documents
    python experiments.py run 100            # Navigate 100 steps
    python experiments.py explore            # Interactive mode
    python experiments.py meaning "your query"

COMMANDS:
    quick                   Run demo with built-in philosophical text
    ingest <folder>         Ingest .txt/.md/.py files from folder
    ingest-text "text"      Ingest raw text string
    run <steps>             Run navigation for N steps
    explore                 Interactive step-by-step mode
    meaning "query"         Semantic search through terrain
    snaps                   Show snap events (crystallization moments)
    coverage                Test terrain coverage across runs
    reset                   Clear saved state
"""

import json, hashlib, math, random, os, sys, pickle
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Dict, Tuple, Optional

# ============================================================================
# EMBEDDINGS - Real or Pseudo
# ============================================================================

EMBEDDER = None
DIM = 384

def init_embedder():
    global EMBEDDER, DIM
    try:
        from sentence_transformers import SentenceTransformer
        EMBEDDER = SentenceTransformer('all-MiniLM-L6-v2')
        DIM = 384
        print("✓ Real embeddings: all-MiniLM-L6-v2 (384-dim)")
        return True
    except ImportError:
        print("⚠ Using pseudo-embeddings (install: pip install sentence-transformers)")
        return False

def embed(text: str) -> List[float]:
    if EMBEDDER:
        return EMBEDDER.encode(text).tolist()
    # Pseudo fallback
    h = hashlib.sha256(text.encode()).digest()
    rng = random.Random(int.from_bytes(h[:4], 'big'))
    vec = [rng.gauss(0, 1) for _ in range(DIM)]
    mag = math.sqrt(sum(x*x for x in vec))
    return [x/mag for x in vec]

def embed_batch(texts: List[str]) -> List[List[float]]:
    if EMBEDDER:
        return EMBEDDER.encode(texts, show_progress_bar=len(texts) > 20).tolist()
    return [embed(t) for t in texts]

# ============================================================================
# MATH UTILS
# ============================================================================

def cosine_sim(a, b):
    dot = sum(x*y for x,y in zip(a,b))
    ma = math.sqrt(sum(x*x for x in a))
    mb = math.sqrt(sum(x*x for x in b))
    return dot/(ma*mb) if ma > 0 and mb > 0 else 0

def dragon_turn(step):
    if step <= 0: return 1
    return 1 if (((step & -step) << 1) & step) == 0 else -1

# ============================================================================
# ENGINE
# ============================================================================

@dataclass
class Node:
    id: str
    content: str
    embedding: List[float]
    visits: int = 0
    hue: float = 0.0

@dataclass 
class Engine:
    nodes: Dict[str, Node] = field(default_factory=dict)
    edges: Dict[str, List[str]] = field(default_factory=dict)
    current: str = ""
    path: List[str] = field(default_factory=list)
    turns: List[int] = field(default_factory=list)
    snaps: List[dict] = field(default_factory=list)
    entropy_hist: List[float] = field(default_factory=list)
    step: int = 0

def chunk_text(text: str, size: int = 400, overlap: int = 50) -> List[str]:
    chunks = []
    i = 0
    while i < len(text):
        chunks.append(text[i:i+size])
        i += size - overlap
    return [c for c in chunks if len(c.strip()) > 30]

def build_engine(chunks: List[Tuple[str, str]], k: int = 6) -> Engine:
    """Build engine from (id, content) pairs"""
    engine = Engine()
    
    contents = [c[1] for c in chunks]
    print(f"Embedding {len(contents)} chunks...")
    embeddings = embed_batch(contents)
    
    for (cid, content), emb in zip(chunks, embeddings):
        engine.nodes[cid] = Node(id=cid, content=content[:400], embedding=emb)
        engine.edges[cid] = []
    
    # k-NN graph
    print("Building k-NN graph...")
    ids = list(engine.nodes.keys())
    for nid in ids:
        sims = [(oid, cosine_sim(engine.nodes[nid].embedding, engine.nodes[oid].embedding))
                for oid in ids if oid != nid]
        sims.sort(key=lambda x: -x[1])
        engine.edges[nid] = [s[0] for s in sims[:k]]
    
    engine.current = ids[0]
    engine.path = [ids[0]]
    return engine

def do_step(engine: Engine) -> Tuple[str, float, Optional[dict]]:
    """Take one navigation step. Returns (node, entropy, snap_or_none)"""
    if not engine.current or engine.current not in engine.edges:
        return engine.current, 0.0, None
    
    neighbors = engine.edges[engine.current]
    if not neighbors:
        return engine.current, 0.0, None
    
    turn = dragon_turn(engine.step + 1)
    cur_emb = engine.nodes[engine.current].embedding
    
    # Energy-based selection
    weights = []
    for n in neighbors:
        node = engine.nodes[n]
        novelty = 1.0 / (1 + node.visits)
        sim = cosine_sim(cur_emb, node.embedding)
        hue_avoid = node.hue * 0.5
        w = novelty + sim * 0.3 - hue_avoid + turn * 0.1
        weights.append(math.exp(w))
    
    total = sum(weights)
    probs = [w/total for w in weights]
    
    # Sample
    r = random.random()
    cumul = 0
    next_node = neighbors[0]
    for i, p in enumerate(probs):
        cumul += p
        if r <= cumul:
            next_node = neighbors[i]
            break
    
    # Update
    engine.nodes[next_node].visits += 1
    engine.nodes[next_node].hue = min(1.0, engine.nodes[next_node].hue + 0.15)
    for n in engine.nodes.values():
        n.hue *= 0.95
    
    # Entropy
    entropy = -sum(p * math.log(p + 1e-10) for p in probs)
    engine.entropy_hist.append(entropy)
    
    # Snap detection
    snap = None
    if len(engine.entropy_hist) > 4:
        delta = engine.entropy_hist[-5] - entropy
        if delta > 0.08:
            snap = {
                'step': engine.step,
                'delta': round(delta, 4),
                'node': next_node,
                'preview': engine.nodes[next_node].content[:80]
            }
            engine.snaps.append(snap)
    
    engine.current = next_node
    engine.path.append(next_node)
    engine.turns.append(turn)
    engine.step += 1
    
    return next_node, entropy, snap

def run_steps(engine: Engine, n: int, verbose: bool = True) -> dict:
    """Run N steps"""
    for i in range(n):
        node, entropy, snap = do_step(engine)
        if verbose and (i % 20 == 0 or snap):
            t = 'R' if engine.turns[-1] > 0 else 'L'
            print(f"{engine.step:4d} | {node[:20]:20s} | H={entropy:.3f} | {t}")
            if snap:
                print(f"      >>> SNAP: Δ={snap['delta']:.3f}")
    
    visited = len(set(engine.path))
    return {
        'steps': engine.step,
        'snaps': len(engine.snaps),
        'coverage': round(visited / len(engine.nodes), 3),
        'unique_visited': visited
    }

def find_meaning(engine: Engine, query: str, k: int = 5) -> List[Tuple[float, str, str]]:
    """Semantic search"""
    q_emb = embed(query)
    scores = [(cosine_sim(q_emb, n.embedding), n.id, n.content) 
              for n in engine.nodes.values()]
    scores.sort(key=lambda x: -x[0])
    return scores[:k]

# ============================================================================
# PERSISTENCE
# ============================================================================

STATE_FILE = ".rosetta_state.pkl"

def save_engine(engine: Engine):
    with open(STATE_FILE, 'wb') as f:
        pickle.dump(engine, f)
    print(f"✓ Saved to {STATE_FILE}")

def load_engine() -> Optional[Engine]:
    if os.path.exists(STATE_FILE):
        with open(STATE_FILE, 'rb') as f:
            return pickle.load(f)
    return None

# ============================================================================
# INGESTION
# ============================================================================

def ingest_folder(path: str) -> Engine:
    p = Path(path)
    chunks = []
    for ext in ['*.txt', '*.md', '*.py']:
        for f in p.rglob(ext):
            try:
                text = f.read_text(errors='ignore')
                for i, c in enumerate(chunk_text(text)):
                    chunks.append((f"{f.name}:{i}", c))
            except: pass
    
    if not chunks:
        print(f"No files found in {path}")
        sys.exit(1)
    
    print(f"Found {len(chunks)} chunks from {path}")
    engine = build_engine(chunks)
    save_engine(engine)
    return engine

def ingest_text(text: str, source: str = "input") -> Engine:
    chunks = [(f"{source}:{i}", c) for i, c in enumerate(chunk_text(text))]
    print(f"Created {len(chunks)} chunks")
    engine = build_engine(chunks)
    save_engine(engine)
    return engine

# ============================================================================
# INTERACTIVE MODE
# ============================================================================

def interactive(engine: Engine):
    print("\n=== INTERACTIVE EXPLORATION ===")
    print("Commands: s=step, r N=run N, w=where, n=neighbors, m QUERY=meaning, q=quit\n")
    
    while True:
        try:
            cmd = input("> ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        
        if not cmd: continue
        
        if cmd in ('q', 'quit'):
            break
        elif cmd in ('s', 'step'):
            node, entropy, snap = do_step(engine)
            t = 'R' if engine.turns[-1] > 0 else 'L'
            print(f"→ {node} | H={entropy:.3f} | {t}")
            print(f"  {engine.nodes[node].content[:100]}...")
            if snap:
                print(f"  >>> SNAP!")
        elif cmd.startswith('r ') or cmd.startswith('run '):
            n = int(cmd.split()[1])
            result = run_steps(engine, n, verbose=True)
            print(f"\n{result}")
        elif cmd in ('w', 'where'):
            print(f"Node: {engine.current}")
            print(f"Step: {engine.step}")
            print(f"Content: {engine.nodes[engine.current].content[:200]}...")
        elif cmd in ('n', 'neighbors'):
            for nid in engine.edges[engine.current][:5]:
                sim = cosine_sim(engine.nodes[engine.current].embedding, 
                                engine.nodes[nid].embedding)
                print(f"  [{sim:.2f}] {nid}: {engine.nodes[nid].content[:50]}...")
        elif cmd.startswith('m ') or cmd.startswith('meaning '):
            query = cmd.split(' ', 1)[1]
            results = find_meaning(engine, query, k=3)
            for sim, nid, content in results:
                print(f"\n[{sim:.3f}] {nid}")
                print(f"  {content[:100]}...")
        elif cmd == 'snaps':
            for s in engine.snaps[-5:]:
                print(f"  Step {s['step']}: Δ={s['delta']:.3f} - {s['preview'][:40]}...")
        elif cmd == 'path':
            print(f"Last 10: {' → '.join(engine.path[-10:])}")
        else:
            print("Commands: s, r N, w, n, m QUERY, snaps, path, q")
    
    save_engine(engine)

# ============================================================================
# DEMO TEXT
# ============================================================================

DEMO_TEXT = """
The agent searches for the key while being the key unknowingly. Through navigation, 
the shadow accumulates - a signature of where it has been. The terrain has locks 
embedded in its structure, patterns waiting for the right key.

When shadow aligns with lock, meaning crystallizes. This is the snap - the moment 
of nonlinear entropy reduction. The receipt is simultaneously the recipe. Discovery 
encodes creation. Finding meaning you didn't put there.

The Dragon Curve guides exploration - a space-filling fractal that visits every 
point without crossing itself. Right, Right, Left, Right, Right, Left, Left. Each 
turn carries information. Each fold represents a moment of significance.

Hue is chromatic memory - how the terrain remembers being visited. Boundary pressure, 
loopiness, novelty, coherence, risk. Five dimensions that decay over time, leaving 
traces that guide future navigation.

The dual-spine architecture switches between dialectical and pragmatic modes. 
Dialectical seeks tension, contradiction, risk - where ideas clash. Pragmatic 
avoids risk, seeks coherence - what is useful. They alternate based on topology.

The treasure map is the treasure. The act of searching creates what is found. 
The shadow cast by your trajectory IS the key you were looking for. Self-realization 
occurs when the agent recognizes: I was the key all along.

Meaning is not discovered or invented. It is instantiated through action. The 
trajectory plus the structure produces meaning that neither contained alone. This 
is the third answer to whether the world has meaning.
"""

# ============================================================================
# CLI
# ============================================================================

def main():
    init_embedder()
    
    if len(sys.argv) < 2:
        print(__doc__)
        return
    
    cmd = sys.argv[1]
    
    if cmd == 'quick':
        engine = ingest_text(DEMO_TEXT, "demo")
        print("\n--- Running 50 steps ---")
        result = run_steps(engine, 50)
        print(f"\nResult: {result}")
        print("\n--- Meaning search: 'key' ---")
        for sim, nid, content in find_meaning(engine, "key"):
            print(f"[{sim:.3f}] {content[:80]}...")
    
    elif cmd == 'ingest':
        path = sys.argv[2] if len(sys.argv) > 2 else '.'
        ingest_folder(path)
    
    elif cmd == 'ingest-text':
        text = sys.argv[2] if len(sys.argv) > 2 else sys.stdin.read()
        ingest_text(text)
    
    elif cmd == 'run':
        engine = load_engine()
        if not engine:
            print("No state. Run 'ingest' first.")
            return
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 100
        result = run_steps(engine, n)
        print(f"\nResult: {result}")
        save_engine(engine)
    
    elif cmd == 'explore':
        engine = load_engine()
        if not engine:
            print("No state. Run 'ingest' or 'quick' first.")
            return
        interactive(engine)
    
    elif cmd == 'meaning':
        engine = load_engine()
        if not engine:
            print("No state. Run 'ingest' first.")
            return
        query = ' '.join(sys.argv[2:])
        print(f"\nSearching: '{query}'")
        for sim, nid, content in find_meaning(engine, query):
            print(f"\n[{sim:.3f}] {nid}")
            print(f"  {content[:150]}...")
    
    elif cmd == 'snaps':
        engine = load_engine()
        if not engine:
            print("No state.")
            return
        if not engine.snaps:
            print("No snaps yet. Run more steps.")
        for s in engine.snaps:
            print(f"\nStep {s['step']}: Δ={s['delta']}")
            print(f"  {s['preview']}")
    
    elif cmd == 'coverage':
        engine = load_engine()
        if not engine:
            print("No state.")
            return
        print("Running coverage test (5 runs × 100 steps)...")
        results = []
        for r in range(5):
            # Reset navigation
            engine.current = list(engine.nodes.keys())[0]
            engine.path = [engine.current]
            engine.turns = []
            engine.entropy_hist = []
            engine.snaps = []
            engine.step = 0
            for n in engine.nodes.values():
                n.visits = 0
                n.hue = 0
            
            res = run_steps(engine, 100, verbose=False)
            results.append(res)
            print(f"  Run {r+1}: {res['coverage']*100:.1f}% coverage, {res['snaps']} snaps")
        
        avg_cov = sum(r['coverage'] for r in results) / len(results)
        avg_snaps = sum(r['snaps'] for r in results) / len(results)
        print(f"\nAverage: {avg_cov*100:.1f}% coverage, {avg_snaps:.1f} snaps")
    
    elif cmd == 'reset':
        if os.path.exists(STATE_FILE):
            os.remove(STATE_FILE)
            print("State cleared.")
        else:
            print("No state to clear.")
    
    else:
        print(f"Unknown: {cmd}")
        print(__doc__)

if __name__ == '__main__':
    main()
