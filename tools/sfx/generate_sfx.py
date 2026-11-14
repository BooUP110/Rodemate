"""Generate Roadmate UI sound effects from scratch (no third-party samples, no license).

Run from the project root:  python -I tools/sfx/generate_sfx.py
Writes 16-bit mono WAV files to app/src/main/res/raw/.

One palette keeps the set from sounding cheap: every cue is the same soft celesta-like
bell, in D major pentatonic, through the same short plate reverb. Taps stay quiet;
rewards and upgrades carry the weight. Low rumble (<120 Hz) is cut for car speakers.
"""
import wave
from pathlib import Path

import numpy as np
from scipy.signal import butter, fftconvolve, sosfilt

SR = 44_100
OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"
rng = np.random.default_rng(7)  # fixed seed: identical files on every run

SEMITONES = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3,
             "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}


def hz(name: str) -> float:
    return 440.0 * 2 ** ((SEMITONES[name[:-1]] + 12 * (int(name[-1]) - 4)) / 12)


def times(seconds: float) -> np.ndarray:
    return np.arange(int(SR * seconds)) / SR


def soft_attack(t: np.ndarray, seconds: float) -> np.ndarray:
    x = np.minimum(t / seconds, 1.0)
    return 0.5 - 0.5 * np.cos(np.pi * x)


def release(signal: np.ndarray, seconds: float = 0.04) -> np.ndarray:
    """Fade every element to exact zero so no cut-off end turns into a click."""
    n = min(int(SR * seconds), len(signal))
    out = signal.copy()
    out[-n:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, n))
    return out


def band(signal: np.ndarray, low: float, high: float) -> np.ndarray:
    return sosfilt(butter(2, [low, high], "bandpass", fs=SR, output="sos"), signal)


def bell(note: str, seconds: float = 1.3, decay: float = 0.45, level: float = 1.0) -> np.ndarray:
    """Celesta-like bell: octave partial plus faint inharmonic shimmer, lightly chorused."""
    t = times(seconds)
    f0 = hz(note)
    out = np.zeros_like(t)
    for ratio, amp, life in ((1.0, 1.0, 1.0), (2.0, 0.36, 0.55), (3.01, 0.10, 0.32), (4.16, 0.05, 0.2)):
        f = f0 * ratio
        if f > SR * 0.42:
            continue
        # A quieter detuned voice adds warmth without the deep tremolo of two equal voices.
        for cents, voice in ((0.0, 1.0), (4.0, 0.22)):
            out += amp * voice * np.sin(2 * np.pi * f * 2 ** (cents / 1200) * t) * np.exp(-t / (decay * life))
    mallet = band(rng.standard_normal(len(t)), 2500, 7500) * np.exp(-t / 0.005) * 0.05
    return release(level * (out * soft_attack(t, 0.004) + mallet))


def pop(note: str, seconds: float, decay: float, bend: float, level: float) -> np.ndarray:
    """Short pitched pop: a quick downward bend gives it a round, physical body."""
    t = times(seconds)
    f = hz(note) * (1 + bend * np.exp(-t / 0.012))
    phase = 2 * np.pi * np.cumsum(f) / SR
    body = (np.sin(phase) + 0.18 * np.sin(2.7 * phase)) * np.exp(-t / decay)
    return release(level * body * soft_attack(t, 0.0025), 0.02)


def mix(*events: tuple[float, np.ndarray]) -> np.ndarray:
    length = max(int(start * SR) + len(sig) for start, sig in events)
    out = np.zeros(length)
    for start, sig in events:
        i = int(start * SR)
        out[i:i + len(sig)] += sig
    return out


def plate(signal: np.ndarray, wet: float, seconds: float) -> np.ndarray:
    """Synthetic plate: exponentially decaying filtered noise, -60 dB at `seconds`."""
    n = int(SR * seconds)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t * 6.9 / seconds)
    ir = sosfilt(butter(1, 5500, "lowpass", fs=SR, output="sos"), ir)
    ir[: int(SR * 0.014)] = 0.0  # pre-delay keeps the dry hit clear
    ir /= np.sqrt(np.sum(ir ** 2))
    wet_signal = fftconvolve(signal, ir)
    dry = np.concatenate([signal, np.zeros(len(wet_signal) - len(signal))])
    return dry * (1 - wet) + wet_signal * wet


def finish(signal: np.ndarray, peak_db: float) -> np.ndarray:
    out = sosfilt(butter(2, 120, "highpass", fs=SR, output="sos"), signal)
    out = sosfilt(butter(2, 10_000, "lowpass", fs=SR, output="sos"), out)
    loud = np.nonzero(np.abs(out) > np.max(np.abs(out)) * 10 ** (-60 / 20))[0]
    out = out[: loud[-1] + 1]
    fade = min(int(SR * 0.03), len(out))
    out[-fade:] *= np.linspace(1.0, 0.0, fade)
    return out / np.max(np.abs(out)) * 10 ** (peak_db / 20)


def tap() -> np.ndarray:
    hit = pop("D6", 0.09, decay=0.018, bend=0.08, level=1.0)
    click = release(band(rng.standard_normal(len(hit)), 3000, 8000) * np.exp(-times(0.09) / 0.0015) * 0.12, 0.02)
    return finish(plate(hit + click, wet=0.08, seconds=0.35), -14)


def locked() -> np.ndarray:
    low = mix((0.0, pop("F#4", 0.12, 0.035, 0.12, 1.0)), (0.11, pop("D4", 0.14, 0.04, 0.12, 0.85)))
    low = sosfilt(butter(2, 1600, "lowpass", fs=SR, output="sos"), low)
    return finish(plate(low, wet=0.10, seconds=0.5), -12)


def reward() -> np.ndarray:
    sparkle = [(0.17, "F#7"), (0.23, "A7"), (0.30, "D7"), (0.38, "E7")]
    events = [(0.0, bell("D6", decay=0.50)), (0.0, bell("D5", decay=0.60, level=0.32)),
              (0.085, bell("A6", decay=0.55, level=0.80))]
    events += [(start, bell(note, seconds=0.3, decay=0.06, level=0.15)) for start, note in sparkle]
    return finish(plate(mix(*events), wet=0.25, seconds=1.3), -3)


def upgrade() -> np.ndarray:
    notes = ["D5", "F#5", "A5", "D6", "F#6", "A6"]
    arpeggio = [(i * 0.055, bell(n, decay=0.5 + 0.04 * i, level=0.6 + 0.08 * i)) for i, n in enumerate(notes)]
    t = times(2.0)
    swell = np.sin(np.pi * np.minimum(t / 0.5, 1.0) / 2) ** 2 * np.exp(-np.maximum(t - 0.5, 0) / 0.45)
    pad = sum(voice * np.sin(2 * np.pi * hz(n) * 2 ** (c / 1200) * t)
              for n in ("D4", "A4", "D5", "F#5") for c, voice in ((0.0, 1.0), (7.0, 0.35)))
    pad = release(sosfilt(butter(2, 3000, "lowpass", fs=SR, output="sos"), pad * swell) * 0.06, 0.2)
    shimmer = [(0.36, bell("D7", decay=0.35, level=0.35)), (0.42, bell("A7", decay=0.30, level=0.20))]
    return finish(plate(mix(*arpeggio, (0.0, pad), *shimmer), wet=0.30, seconds=1.8), -2)


def postcard() -> np.ndarray:
    t = times(0.32)
    envelope = np.where(t < 0.09, np.sin(np.pi * t / 0.18) ** 2, np.exp(-(t - 0.09) / 0.07))
    noise = rng.standard_normal(len(t))
    mixer = np.minimum(t / 0.25, 1.0)  # paper "swish" rises in brightness
    swish = release((band(noise, 1200, 2600) * (1 - mixer) + band(noise, 3000, 6500) * mixer) * envelope * 0.35)
    events = [(0.0, swish), (0.16, bell("F#6", decay=0.40, level=0.55)), (0.19, bell("D7", decay=0.25, level=0.20))]
    return finish(plate(mix(*events), wet=0.20, seconds=1.1), -5)


CUES = {"ui_tap": tap, "ui_locked": locked, "reward_claim": reward,
        "pass_upgrade": upgrade, "postcard_open": postcard}


def write(name: str, signal: np.ndarray) -> None:
    pcm = np.round(signal * 32767).astype("<i2")
    with wave.open(str(OUT / f"{name}.wav"), "wb") as file:
        file.setnchannels(1)
        file.setsampwidth(2)
        file.setframerate(SR)
        file.writeframes(pcm.tobytes())


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, build in CUES.items():
        sound = build()
        assert np.all(np.isfinite(sound)) and np.max(np.abs(sound)) <= 1.0, name
        assert 0.05 < len(sound) / SR < 3.0, name
        write(name, sound)
        print(f"{name:14s} {len(sound) / SR:5.2f}s  peak {20 * np.log10(np.max(np.abs(sound))):6.1f} dBFS")
