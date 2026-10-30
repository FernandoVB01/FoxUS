from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CANONICAL_AUDIO_DIR = REPO_ROOT / "assets" / "audio"
ANDROID_RAW_DIR = REPO_ROOT / "android" / "app" / "src" / "main" / "res" / "raw"

SAMPLE_RATE = 22050


def _write_pcm16_mono(samples: list[float], paths: list[Path]) -> int:
    pcm = bytearray()
    for s in samples:
        clamped = max(-0.98, min(0.98, s))
        pcm.extend(struct.pack("<h", int(clamped * 32767.0)))
    data_bytes = bytes(pcm)
    for target in paths:
        target.parent.mkdir(parents=True, exist_ok=True)
        with wave.open(str(target), "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(SAMPLE_RATE)
            wf.writeframes(data_bytes)
    return len(data_bytes)


def _adsr_note(
    freq_hz: float,
    duration_s: float,
    amp: float = 0.35,
    attack_s: float = 0.012,
    harmonic2: float = 0.22,
    harmonic3: float = 0.08,
) -> list[float]:
    n = int(SAMPLE_RATE * duration_s)
    attack_n = max(1, int(SAMPLE_RATE * attack_s))
    out: list[float] = []
    two_pi = 2.0 * math.pi
    for i in range(n):
        t = i / SAMPLE_RATE
        if i < attack_n:
            env = 0.5 * (1.0 - math.cos(math.pi * i / attack_n))
        else:
            progress = (i - attack_n) / max(1, n - attack_n)
            env = math.exp(-3.4 * progress) * (1.0 - progress**3)
        wave_val = (
            math.sin(two_pi * freq_hz * t)
            + harmonic2 * math.sin(two_pi * 2.0 * freq_hz * t)
            + harmonic3 * math.sin(two_pi * 3.0 * freq_hz * t)
        )
        out.append(amp * env * wave_val / (1.0 + harmonic2 + harmonic3))
    return out


def _mix_sequence(events: list[tuple[float, list[float]]], total_duration_s: float) -> list[float]:
    total_n = int(SAMPLE_RATE * total_duration_s)
    buf = [0.0] * total_n
    for start_s, samples in events:
        offset = int(SAMPLE_RATE * start_s)
        for idx, val in enumerate(samples):
            pos = offset + idx
            if 0 <= pos < total_n:
                buf[pos] += val
    # Soft limiter
    peak = max((abs(x) for x in buf), default=1.0)
    if peak > 0.92:
        scale = 0.90 / peak
        buf = [x * scale for x in buf]
    return buf


def _synth_fly_buzz() -> list[float]:
    """Playful, gentle, short non-intrusive virtual fly flutter + marimba wink (~0.62s)."""
    dur = 0.62
    n = int(SAMPLE_RATE * dur)
    two_pi = 2.0 * math.pi
    samples: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        # Smooth two-swoop envelope ("bzz-bzz!")
        window = math.sin(math.pi * (t / dur)) ** 0.7
        pulse = 0.55 + 0.45 * math.sin(two_pi * 5.0 * t)
        fm = 196.0 + 18.0 * math.sin(two_pi * 26.0 * t)
        flutter = (
            0.50 * math.sin(two_pi * fm * t)
            + 0.30 * math.sin(two_pi * 2.0 * fm * t)
            + 0.20 * math.sin(two_pi * 3.0 * fm * t)
        )
        samples.append(0.22 * window * pulse * flutter)

    # Add a cute bright marimba pluck at the end so it feels musical & friendly
    wink = _adsr_note(880.0, 0.18, amp=0.26, harmonic2=0.15, harmonic3=0.0)
    return _mix_sequence([(0.0, samples), (0.40, wink)], dur)


def _synth_focus_ambient_loop() -> list[float]:
    """
    Exact 12.0-second seamless instrumental loop for Focus Mode.
    All partial frequencies are exact integer multiples of (1 / 12.0) Hz so phase(0) == phase(12.0s).
    """
    dur = 12.0
    n = int(SAMPLE_RATE * dur)
    two_pi = 2.0 * math.pi
    # Warm Dadd9 / Bm7 ambient harmonic frequencies (exact integer cycles in 12s)
    partials = [
        (147.0, 0.28, 1),   # ~D3
        (220.5, 0.22, 2),   # ~A3
        (294.0, 0.20, 1),   # ~D4
        (330.75, 0.14, 3),  # ~E4 (330.75 * 12 = 3969 exact cycles!)
        (367.5, 0.16, 2),   # ~F#4 (367.5 * 12 = 4410 exact cycles!)
        (441.0, 0.12, 1),   # ~A4
    ]
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        sample = 0.0
        for freq, amp, lfo_cycles in partials:
            # Periodic breathing LFO with exact integer cycles over 12.0s
            lfo = 0.72 + 0.28 * math.cos(two_pi * (lfo_cycles / dur) * t)
            sample += amp * lfo * math.sin(two_pi * freq * t)
        out.append(sample * 0.42)
    return out


def _synth_referee_whistle() -> list[float]:
    """Crisp sports referee dual-tone pea-whistle kickoff (short pip + strong blast)."""
    dur = 0.62
    n = int(SAMPLE_RATE * dur)
    two_pi = 2.0 * math.pi
    out: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        # Two blasts: 0.00-0.14s short pip, 0.19-0.58s kickoff blast
        if t < 0.14:
            env = min(1.0, t / 0.012) * min(1.0, (0.14 - t) / 0.025)
        elif 0.19 <= t <= 0.58:
            env = min(1.0, (t - 0.19) / 0.015) * min(1.0, (0.58 - t) / 0.06)
        else:
            env = 0.0
        pea_trill = 0.68 + 0.32 * math.sin(two_pi * 38.0 * t)
        f1 = math.sin(two_pi * 2820.0 * t)
        f2 = math.sin(two_pi * 3095.0 * t)
        out.append(0.36 * env * pea_trill * (0.58 * f1 + 0.42 * f2))
    return out


def main() -> int:
    catalog: dict[str, list[float]] = {
        "fox_appear": _mix_sequence(
            [
                (0.00, _adsr_note(587.33, 0.24, amp=0.32)),  # D5
                (0.11, _adsr_note(880.00, 0.30, amp=0.34)),  # A5
            ],
            0.44,
        ),
        "challenge_start": _mix_sequence(
            [
                (0.00, _adsr_note(587.33, 0.18, amp=0.32)),   # D5
                (0.10, _adsr_note(739.99, 0.18, amp=0.34)),   # F#5
                (0.20, _adsr_note(880.00, 0.20, amp=0.36)),   # A5
                (0.31, _adsr_note(1174.66, 0.28, amp=0.38)),  # D6
            ],
            0.62,
        ),
        "mission_accept": _mix_sequence(
            [
                (0.00, _adsr_note(523.25, 0.20, amp=0.32)),  # C5
                (0.09, _adsr_note(659.25, 0.28, amp=0.36)),  # E5
                (0.09, _adsr_note(783.99, 0.28, amp=0.22)),  # G5
            ],
            0.40,
        ),
        "mission_reject": _mix_sequence(
            [
                (0.00, _adsr_note(523.25, 0.18, amp=0.25, harmonic2=0.10, harmonic3=0.0)),
                (0.12, _adsr_note(392.00, 0.24, amp=0.24, harmonic2=0.08, harmonic3=0.0)),
            ],
            0.40,
        ),
        "mission_complete": _mix_sequence(
            [
                (0.00, _adsr_note(587.33, 0.26, amp=0.30)),   # D5
                (0.12, _adsr_note(739.99, 0.26, amp=0.32)),   # F#5
                (0.24, _adsr_note(880.00, 0.28, amp=0.34)),   # A5
                (0.36, _adsr_note(1108.73, 0.30, amp=0.34)),  # C#6
                (0.48, _adsr_note(1174.66, 0.44, amp=0.40)),  # D6
            ],
            0.95,
        ),
        "xp_gain": _mix_sequence(
            [
                (0.00, _adsr_note(880.00, 0.20, amp=0.30)),   # A5
                (0.09, _adsr_note(1318.51, 0.28, amp=0.34)),  # E6
            ],
            0.42,
        ),
        "level_up": _mix_sequence(
            [
                (0.00, _adsr_note(587.33, 0.22, amp=0.32)),
                (0.11, _adsr_note(739.99, 0.22, amp=0.32)),
                (0.22, _adsr_note(880.00, 0.24, amp=0.34)),
                (0.34, _adsr_note(1174.66, 0.30, amp=0.36)),
                (0.52, _adsr_note(1479.98, 0.55, amp=0.38)),
                (0.52, _adsr_note(880.00, 0.55, amp=0.22)),
            ],
            1.12,
        ),
        "fly_buzz": _synth_fly_buzz(),
        "fox_sleep": _mix_sequence(
            [
                (0.00, _adsr_note(659.25, 0.28, amp=0.22, harmonic2=0.05, harmonic3=0.0)),
                (0.20, _adsr_note(523.25, 0.30, amp=0.20, harmonic2=0.05, harmonic3=0.0)),
                (0.40, _adsr_note(392.00, 0.36, amp=0.18, harmonic2=0.04, harmonic3=0.0)),
            ],
            0.80,
        ),
        "focus_ambient_loop": _synth_focus_ambient_loop(),
        "referee_whistle": _synth_referee_whistle(),
        "crowd_cheer": _mix_sequence(
            [
                (0.00, _adsr_note(523.25, 0.22, amp=0.30)),   # C5
                (0.08, _adsr_note(659.25, 0.24, amp=0.32)),   # E5
                (0.16, _adsr_note(783.99, 0.32, amp=0.36)),   # G5
                (0.16, _adsr_note(1046.50, 0.42, amp=0.38)),  # C6 fanfare
            ],
            0.62,
        ),
    }

    for sound_name, samples in catalog.items():
        targets = [
            CANONICAL_AUDIO_DIR / f"{sound_name}.wav",
            ANDROID_RAW_DIR / f"{sound_name}.wav",
        ]
        byte_len = _write_pcm16_mono(samples, targets)
        print(f"Generated {sound_name}.wav ({byte_len} PCM bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
