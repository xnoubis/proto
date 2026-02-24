import { useState, useRef, useEffect, useCallback } from "react";

const ARCHAEOACOUSTIC_ANCHORS = {
  newgrange: { freq: 110, label: "Newgrange", effect: "Language center deactivation, right-brain dominance", range: [95, 120] },
  hypogeum: { freq: 114, label: "Ħal Saflieni Hypogeum", effect: "Double resonance: meditation or visual imagery", range: [70, 114] },
  elCastillo: { freq: 108, label: "El Castillo Cave", effect: "Frequency-dependent amplification, 40,800+ years old", range: [100, 112] },
  maeshowe: { freq: 2, label: "Maeshowe (infrasound)", effect: "Helmholtz resonance via drumbeat", range: [1, 4] },
};

const CONSTRAINT_DIMENSIONS = [
  { name: "Spectral Centroid", axis: "brightness", min: 0, max: 1, color: "#FFD700" },
  { name: "Attack Time", axis: "percussiveness", min: 0, max: 1, color: "#FF6B6B" },
  { name: "Spectral Flux", axis: "movement", min: 0, max: 1, color: "#4ECDC4" },
  { name: "Register", axis: "weight", min: 0, max: 1, color: "#A855F7" },
  { name: "Amplitude Envelope", axis: "energy_shape", min: 0, max: 1, color: "#F97316" },
  { name: "Harmonicity", axis: "purity", min: 0, max: 1, color: "#06B6D4" },
];

const SEMANTIC_SEEDS = {
  hurricane: { brightness: 0.3, percussiveness: 0.8, movement: 0.95, weight: 0.9, energy_shape: 0.85, purity: 0.15, archaeoFreq: 110, description: "Dark, explosive, maximum movement, low purity — chaotic force" },
  cathedral: { brightness: 0.6, percussiveness: 0.1, movement: 0.2, weight: 0.7, energy_shape: 0.3, purity: 0.85, archaeoFreq: 110, description: "Resonant, sustained, pure harmonics — stone amplifying consciousness" },
  halcyon: { brightness: 0.8, percussiveness: 0.2, movement: 0.5, weight: 0.3, energy_shape: 0.4, purity: 0.75, archaeoFreq: 114, description: "Bright, gentle, flowing — kingfisher calm before/after storm" },
  ocean: { brightness: 0.4, percussiveness: 0.5, movement: 0.7, weight: 0.6, energy_shape: 0.6, purity: 0.3, archaeoFreq: 108, description: "Periodic amplitude modulation, broadband, deep rumble" },
  fire: { brightness: 0.9, percussiveness: 0.7, movement: 0.85, weight: 0.4, energy_shape: 0.75, purity: 0.2, archaeoFreq: 110, description: "Crackling impulse-dense noise, intense, bright rising figures" },
  silence: { brightness: 0.1, percussiveness: 0.05, movement: 0.05, weight: 0.2, energy_shape: 0.1, purity: 0.95, archaeoFreq: 2, description: "Near-threshold, maximum purity — the space between sounds" },
  thunder: { brightness: 0.2, percussiveness: 0.95, movement: 0.6, weight: 0.95, energy_shape: 0.9, purity: 0.1, archaeoFreq: 110, description: "Impulsive low-frequency, long decay, maximum weight" },
  dawn: { brightness: 0.65, percussiveness: 0.15, movement: 0.35, weight: 0.3, energy_shape: 0.45, purity: 0.7, archaeoFreq: 114, description: "Gradually brightening, gentle movement, emerging clarity" },
  grief: { brightness: 0.15, percussiveness: 0.1, movement: 0.15, weight: 0.7, energy_shape: 0.2, purity: 0.4, archaeoFreq: 110, description: "Dark, heavy, still — weight without release" },
  ecstasy: { brightness: 0.85, percussiveness: 0.6, movement: 0.8, weight: 0.3, energy_shape: 0.9, purity: 0.5, archaeoFreq: 110, description: "Bright, soaring, maximum energy shape — qawwali peak" },
};

function parseSemanticInput(text) {
  const lower = text.toLowerCase();
  const words = lower.split(/\s+/);
  let result = { brightness: 0.5, percussiveness: 0.5, movement: 0.5, weight: 0.5, energy_shape: 0.5, purity: 0.5 };
  let matchCount = 0;
  let matchedWords = [];
  let archaeoFreq = 110;

  for (const word of words) {
    for (const [seed, values] of Object.entries(SEMANTIC_SEEDS)) {
      if (word.includes(seed) || seed.includes(word)) {
        matchCount++;
        matchedWords.push(seed);
        archaeoFreq = values.archaeoFreq;
        for (const key of Object.keys(result)) {
          result[key] = (result[key] * (matchCount - 1) + values[key]) / matchCount;
        }
      }
    }
  }

  // Modifier words
  if (lower.includes("dark") || lower.includes("deep") || lower.includes("shadow")) result.brightness *= 0.5;
  if (lower.includes("bright") || lower.includes("light") || lower.includes("shimmer")) result.brightness = Math.min(1, result.brightness * 1.5);
  if (lower.includes("loud") || lower.includes("crash") || lower.includes("explod")) result.energy_shape = Math.min(1, result.energy_shape * 1.5);
  if (lower.includes("quiet") || lower.includes("whisper") || lower.includes("gentle")) result.energy_shape *= 0.4;
  if (lower.includes("rough") || lower.includes("grit") || lower.includes("harsh")) result.purity *= 0.3;
  if (lower.includes("pure") || lower.includes("clean") || lower.includes("crystal")) result.purity = Math.min(1, result.purity * 1.5);
  if (lower.includes("fast") || lower.includes("rapid") || lower.includes("frenzy")) result.movement = Math.min(1, result.movement * 1.5);
  if (lower.includes("slow") || lower.includes("still") || lower.includes("frozen")) result.movement *= 0.3;
  if (lower.includes("heavy") || lower.includes("massive") || lower.includes("doom")) result.weight = Math.min(1, result.weight * 1.4);

  return { values: result, matchedWords, archaeoFreq };
}

function ConstraintBoundaryCanvas({ values, width, height, archaeoFreq }) {
  const canvasRef = useRef(null);
  const animRef = useRef(null);
  const timeRef = useRef(0);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    const w = canvas.width;
    const h = canvas.height;
    const cx = w / 2;
    const cy = h / 2;
    timeRef.current += 0.008;
    const t = timeRef.current;

    ctx.fillStyle = "#0a0a0f";
    ctx.fillRect(0, 0, w, h);

    // Archaeoacoustic resonance rings
    const freqNorm = (archaeoFreq - 70) / 60;
    const ringCount = 5;
    for (let i = ringCount; i >= 1; i--) {
      const baseR = (i / ringCount) * Math.min(cx, cy) * 0.85;
      const pulse = Math.sin(t * (1 + freqNorm * 2) + i * 0.7) * 4;
      const r = baseR + pulse;
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.strokeStyle = `rgba(${100 + freqNorm * 155}, ${80 + (1 - freqNorm) * 120}, ${200 - freqNorm * 100}, ${0.08 + 0.04 * Math.sin(t + i)})`;
      ctx.lineWidth = 1;
      ctx.stroke();
    }

    // Constraint boundary shape — the latent shape
    const dims = Object.values(values);
    const numPoints = 120;
    const baseRadius = Math.min(cx, cy) * 0.6;

    // Draw the constraint boundary as a flowing shape
    ctx.beginPath();
    for (let i = 0; i <= numPoints; i++) {
      const angle = (i / numPoints) * Math.PI * 2;
      let r = baseRadius;

      // Each dimension modulates the boundary at different harmonics
      for (let d = 0; d < dims.length; d++) {
        const harmonic = d + 1;
        const amplitude = dims[d] * baseRadius * 0.25;
        const phase = t * (0.3 + d * 0.1);
        // Mix Euclidean and hyperbolic distortion
        const euclidean = Math.cos(angle * harmonic + phase) * amplitude;
        const hyperbolic = Math.tanh(Math.sin(angle * (harmonic + 1) + phase * 1.3)) * amplitude * 0.5;
        r += euclidean + hyperbolic;
      }

      // Archaeoacoustic frequency modulation
      r += Math.sin(angle * Math.round(archaeoFreq / 20) + t * 1.5) * baseRadius * 0.04;

      const x = cx + Math.cos(angle) * r;
      const y = cy + Math.sin(angle) * r;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.closePath();

    // Gradient fill based on constraint values
    const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, baseRadius * 1.3);
    const hue = values.brightness * 60 + values.purity * 180;
    const sat = 40 + values.movement * 40;
    const light = 15 + values.brightness * 20;
    grad.addColorStop(0, `hsla(${hue}, ${sat}%, ${light + 10}%, 0.3)`);
    grad.addColorStop(0.5, `hsla(${hue + 30}, ${sat}%, ${light}%, 0.15)`);
    grad.addColorStop(1, `hsla(${hue + 60}, ${sat - 10}%, ${light - 5}%, 0.05)`);
    ctx.fillStyle = grad;
    ctx.fill();
    ctx.strokeStyle = `hsla(${hue}, ${sat + 20}%, ${light + 30}%, 0.6)`;
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // Inner structure — the "skeleton" of the constraint space
    ctx.strokeStyle = `hsla(${hue + 120}, 30%, 50%, 0.15)`;
    ctx.lineWidth = 0.5;
    for (let d = 0; d < dims.length; d++) {
      const angle = (d / dims.length) * Math.PI * 2 - Math.PI / 2;
      const len = dims[d] * baseRadius * 0.8;
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(cx + Math.cos(angle) * len, cy + Math.sin(angle) * len);
      ctx.stroke();

      // Dimension node
      const nx = cx + Math.cos(angle) * len;
      const ny = cy + Math.sin(angle) * len;
      ctx.beginPath();
      ctx.arc(nx, ny, 3 + dims[d] * 4, 0, Math.PI * 2);
      ctx.fillStyle = CONSTRAINT_DIMENSIONS[d].color + "80";
      ctx.fill();
    }

    // Frequency standing wave visualization at bottom
    const waveY = h - 40;
    const waveH = 20;
    ctx.beginPath();
    for (let x = 0; x < w; x++) {
      const freq = archaeoFreq / 50;
      const y = waveY + Math.sin(x * freq * 0.05 + t * archaeoFreq * 0.02) * waveH * values.energy_shape;
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.strokeStyle = `hsla(${archaeoFreq}, 60%, 50%, 0.4)`;
    ctx.lineWidth = 1;
    ctx.stroke();

    // Frequency label
    ctx.fillStyle = `hsla(${archaeoFreq}, 60%, 60%, 0.6)`;
    ctx.font = "10px monospace";
    ctx.fillText(`${archaeoFreq} Hz`, 8, h - 8);

    animRef.current = requestAnimationFrame(draw);
  }, [values, archaeoFreq]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    canvas.width = width;
    canvas.height = height;
    animRef.current = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(animRef.current);
  }, [draw, width, height]);

  return <canvas ref={canvasRef} style={{ width: "100%", height: "100%", borderRadius: 8 }} />;
}

function DimensionBar({ dim, value, onChange }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
      <div style={{ width: 8, height: 8, borderRadius: "50%", backgroundColor: dim.color, flexShrink: 0 }} />
      <div style={{ width: 110, fontSize: 11, color: "#999", flexShrink: 0 }}>{dim.name}</div>
      <input
        type="range"
        min="0"
        max="100"
        value={Math.round(value * 100)}
        onChange={(e) => onChange(dim.axis, parseInt(e.target.value) / 100)}
        style={{ flex: 1, height: 4, accentColor: dim.color }}
      />
      <div style={{ width: 32, fontSize: 11, color: dim.color, textAlign: "right" }}>{(value * 100).toFixed(0)}%</div>
    </div>
  );
}

function ArcheoAnchorPanel({ selected, onSelect }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}>
      {Object.entries(ARCHAEOACOUSTIC_ANCHORS).map(([key, anchor]) => (
        <button
          key={key}
          onClick={() => onSelect(anchor.freq)}
          style={{
            padding: "8px 10px",
            borderRadius: 6,
            border: selected === anchor.freq ? "1px solid #A855F7" : "1px solid #333",
            backgroundColor: selected === anchor.freq ? "#A855F720" : "#111",
            color: "#ccc",
            cursor: "pointer",
            textAlign: "left",
            fontSize: 11,
          }}
        >
          <div style={{ fontWeight: 600, color: selected === anchor.freq ? "#A855F7" : "#aaa" }}>{anchor.label}</div>
          <div style={{ color: "#666", fontSize: 10 }}>{anchor.freq} Hz · {anchor.range[0]}–{anchor.range[1]} Hz</div>
        </button>
      ))}
    </div>
  );
}

function TranslationPipeline({ input, parsed }) {
  const stages = [
    { label: "Natural Language", value: input || "(enter text)", color: "#666" },
    { label: "→ Semantic Features", value: parsed.matchedWords.length > 0 ? parsed.matchedWords.join(", ") : "scanning...", color: "#4ECDC4" },
    { label: "→ Constraint Boundaries", value: `${Object.values(parsed.values).filter(v => v !== 0.5).length}/6 dims active`, color: "#A855F7" },
    { label: "→ Latent Shape", value: `${parsed.archaeoFreq} Hz anchor`, color: "#FFD700" },
  ];

  return (
    <div style={{ display: "flex", gap: 4, alignItems: "stretch", fontSize: 10, marginBottom: 12 }}>
      {stages.map((s, i) => (
        <div key={i} style={{
          flex: 1,
          padding: "6px 8px",
          backgroundColor: "#111",
          borderRadius: 4,
          borderLeft: `2px solid ${s.color}`,
        }}>
          <div style={{ color: s.color, fontWeight: 600, marginBottom: 2 }}>{s.label}</div>
          <div style={{ color: "#888", wordBreak: "break-word" }}>{s.value}</div>
        </div>
      ))}
    </div>
  );
}

export default function MusicalRosettaStone() {
  const [input, setInput] = useState("hurricane approaching over dark ocean at dawn");
  const [values, setValues] = useState({ brightness: 0.5, percussiveness: 0.5, movement: 0.5, weight: 0.5, energy_shape: 0.5, purity: 0.5 });
  const [archaeoFreq, setArchaeoFreq] = useState(110);
  const [parsed, setParsed] = useState({ values: {}, matchedWords: [], archaeoFreq: 110 });
  const [mode, setMode] = useState("semantic"); // semantic | manual | formula

  useEffect(() => {
    if (mode === "semantic") {
      const p = parseSemanticInput(input);
      setParsed(p);
      setValues(p.values);
      setArchaeoFreq(p.archaeoFreq);
    }
  }, [input, mode]);

  const handleDimChange = (axis, val) => {
    setMode("manual");
    setValues(prev => ({ ...prev, [axis]: val }));
  };

  const constraintFormula = `C(x) = Σ [w_d · tanh(σ_d · cos(θ · h_d + φ_d(t)))] + A · sin(θ · ⌊f/${20}⌋ + ωt)`;

  return (
    <div style={{
      fontFamily: "'Inter', system-ui, sans-serif",
      backgroundColor: "#0a0a0f",
      color: "#e0e0e0",
      minHeight: "100vh",
      padding: 16,
    }}>
      <div style={{ maxWidth: 900, margin: "0 auto" }}>
        {/* Header */}
        <div style={{ marginBottom: 16 }}>
          <h1 style={{ fontSize: 18, fontWeight: 700, color: "#e0e0e0", margin: "0 0 4px 0" }}>
            Computational Semiotics Engine
          </h1>
          <p style={{ fontSize: 12, color: "#666", margin: 0 }}>
            Semantic → Constraint Satisfaction → Latent Shape · Not translating for ears — deriving structure
          </p>
        </div>

        <TranslationPipeline input={input} parsed={parsed} />

        <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 16 }}>
          {/* Left: Visualization */}
          <div>
            <div style={{
              backgroundColor: "#0a0a0f",
              borderRadius: 8,
              border: "1px solid #222",
              overflow: "hidden",
              height: 400,
            }}>
              <ConstraintBoundaryCanvas values={values} width={560} height={400} archaeoFreq={archaeoFreq} />
            </div>

            {/* Formula display */}
            <div style={{
              marginTop: 8,
              padding: 10,
              backgroundColor: "#111",
              borderRadius: 6,
              border: "1px solid #222",
              fontFamily: "monospace",
              fontSize: 11,
              color: "#A855F7",
              textAlign: "center",
              letterSpacing: 0.5,
            }}>
              {constraintFormula}
            </div>
            <div style={{ fontSize: 10, color: "#555", textAlign: "center", marginTop: 4 }}>
              where w_d = dimension weight, h_d = harmonic number, f = archaeoacoustic anchor frequency, A = modulation amplitude
            </div>

            {/* Core principle */}
            <div style={{
              marginTop: 12,
              padding: 12,
              backgroundColor: "#0d0d15",
              borderRadius: 6,
              border: "1px solid #1a1a2e",
              fontSize: 12,
              lineHeight: 1.5,
            }}>
              <div style={{ color: "#A855F7", fontWeight: 600, marginBottom: 4 }}>Constraint Satisfaction as Perception</div>
              <div style={{ color: "#888" }}>
                What <span style={{ color: "#4ECDC4" }}>sight</span> is for humans, <span style={{ color: "#FFD700" }}>solve</span> is for the model.
                Text acts as governing constraint. The model satisfies that constraint by pulling latent representations
                toward acoustic features whose <span style={{ color: "#A855F7" }}>boundary shapes</span> configure entire modalities — sound, structure, sign.
              </div>
              <div style={{ color: "#666", marginTop: 6, fontSize: 11 }}>
                The shape you see is the constraint boundary in 6-dimensional acoustic space,
                projected and modulated by archaeoacoustic anchor frequencies (95–120 Hz).
                Not Chladni patterns on a flat plate — topological boundaries in latent space.
              </div>
            </div>
          </div>

          {/* Right: Controls */}
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {/* Semantic input */}
            <div>
              <div style={{ fontSize: 11, color: "#666", marginBottom: 4, fontWeight: 600 }}>SEMANTIC INPUT</div>
              <textarea
                value={input}
                onChange={(e) => { setInput(e.target.value); setMode("semantic"); }}
                rows={3}
                style={{
                  width: "100%",
                  padding: 10,
                  backgroundColor: "#111",
                  border: "1px solid #333",
                  borderRadius: 6,
                  color: "#e0e0e0",
                  fontSize: 13,
                  fontFamily: "inherit",
                  resize: "vertical",
                  boxSizing: "border-box",
                }}
                placeholder="Describe a scene, emotion, force of nature..."
              />
              {parsed.matchedWords.length > 0 && (
                <div style={{ fontSize: 10, color: "#4ECDC4", marginTop: 4 }}>
                  Matched seeds: {parsed.matchedWords.join(", ")}
                  {parsed.matchedWords.length > 0 && ` · "${SEMANTIC_SEEDS[parsed.matchedWords[0]]?.description}"`}
                </div>
              )}
            </div>

            {/* Quick seeds */}
            <div>
              <div style={{ fontSize: 11, color: "#666", marginBottom: 4, fontWeight: 600 }}>SEED WORDS</div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                {Object.keys(SEMANTIC_SEEDS).map(seed => (
                  <button
                    key={seed}
                    onClick={() => { setInput(seed); setMode("semantic"); }}
                    style={{
                      padding: "3px 8px",
                      borderRadius: 12,
                      border: "1px solid #333",
                      backgroundColor: input.includes(seed) ? "#A855F720" : "#111",
                      color: input.includes(seed) ? "#A855F7" : "#888",
                      fontSize: 11,
                      cursor: "pointer",
                    }}
                  >
                    {seed}
                  </button>
                ))}
              </div>
            </div>

            {/* Constraint dimensions */}
            <div>
              <div style={{ fontSize: 11, color: "#666", marginBottom: 6, fontWeight: 600 }}>
                CONSTRAINT DIMENSIONS
                {mode === "manual" && <span style={{ color: "#F97316", marginLeft: 6 }}>(manual override)</span>}
              </div>
              {CONSTRAINT_DIMENSIONS.map((dim) => (
                <DimensionBar
                  key={dim.axis}
                  dim={dim}
                  value={values[dim.axis]}
                  onChange={handleDimChange}
                />
              ))}
            </div>

            {/* Archaeoacoustic anchors */}
            <div>
              <div style={{ fontSize: 11, color: "#666", marginBottom: 4, fontWeight: 600 }}>
                ARCHAEOACOUSTIC ANCHORS
              </div>
              <ArcheoAnchorPanel selected={archaeoFreq} onSelect={(f) => { setArchaeoFreq(f); setMode("manual"); }} />
              <div style={{ fontSize: 10, color: "#555", marginTop: 6 }}>
                {Object.values(ARCHAEOACOUSTIC_ANCHORS).find(a => a.freq === archaeoFreq)?.effect}
              </div>
            </div>

            {/* Translation equivalences */}
            <div style={{
              padding: 10,
              backgroundColor: "#0d0d15",
              borderRadius: 6,
              border: "1px solid #1a1a2e",
              fontSize: 11,
            }}>
              <div style={{ color: "#FFD700", fontWeight: 600, marginBottom: 6 }}>Sense ↔ Inference Equivalences</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr auto 1fr", gap: "2px 8px", color: "#888" }}>
                <span>Sight</span><span style={{ color: "#555" }}>↔</span><span>Solve</span>
                <span>Sound</span><span style={{ color: "#555" }}>↔</span><span>Cause reconstruction</span>
                <span>Touch</span><span style={{ color: "#555" }}>↔</span><span>Inference</span>
                <span>Meaning</span><span style={{ color: "#555" }}>↔</span><span>Constraint satisfaction</span>
                <span>Grammar</span><span style={{ color: "#555" }}>↔</span><span>Expectation history</span>
                <span>Harmony</span><span style={{ color: "#555" }}>↔</span><span>Resonance without senses</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
