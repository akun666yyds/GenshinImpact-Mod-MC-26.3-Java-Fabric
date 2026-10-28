"""Synthesize the mod's custom spell and water spirit sound effects.

Outputs mono 44.1 kHz OGG files into assets/xinkun_genshin/sounds.
"""

import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
ROOT = os.path.join("src", "main", "resources", "assets", "xinkun_genshin", "sounds")
rng = np.random.default_rng(20261023)


def t_axis(duration):
    return np.linspace(0.0, duration, int(SR * duration), endpoint=False)


def envelope(t, attack=0.01, decay=3.0):
    return np.exp(-t * decay) * (1.0 - np.exp(-t / max(attack, 1e-4)))


def glide(t, start, end, curve=1.0):
    freq = start + (end - start) * (t / t[-1]) ** curve
    return np.sin(2.0 * np.pi * np.cumsum(freq) / SR)


def chime(t, freq, decay=2.0, harmonics=(1.0, 0.45, 0.2, 0.1)):
    out = np.zeros_like(t)
    for i, amp in enumerate(harmonics):
        out += amp * np.sin(2.0 * np.pi * freq * (i + 1) * t)
    return out * envelope(t, 0.004, decay)


def noise_burst(duration, low=0.02, high=0.6, decay=5.0):
    t = t_axis(duration)
    white = rng.normal(0, 1, len(t))
    spectrum = np.fft.rfft(white)
    freqs = np.fft.rfftfreq(len(t), 1 / SR)
    mask = np.ones_like(freqs)
    mask[freqs < low * SR] = 0.0
    mask[freqs > high * SR] = 0.0
    shaped = np.fft.irfft(spectrum * mask, len(t))
    return shaped * envelope(t, 0.006, decay)


def bubble(t, start, freq0, freq1, dur=0.09):
    i0 = int(start * SR)
    n = int(dur * SR)
    if i0 + n > len(t):
        n = len(t) - i0
    tt = np.arange(n) / SR
    sweep = np.sin(2 * np.pi * (freq0 * tt + (freq1 - freq0) * tt ** 2 / (2 * dur)))
    return i0, sweep * np.exp(-tt * 38.0)


def add_bubbles(signal, count, window, freq_range=(380, 1400)):
    out = signal.copy()
    for _ in range(count):
        start = rng.uniform(*window)
        f0 = rng.uniform(*freq_range)
        i0, b = bubble(out, start, f0, f0 * rng.uniform(1.4, 2.4))
        out[i0:i0 + len(b)] += b * 0.22
    return out


def reverb(signal, taps=((0.075, 0.32), (0.15, 0.2), (0.27, 0.12))):
    out = signal.copy()
    for delay, gain in taps:
        d = int(delay * SR)
        if d < len(out):
            out[d:] += signal[:-d] * gain
    return out


def normalize(signal, peak=0.86):
    m = np.max(np.abs(signal))
    if m < 1e-9:
        return signal
    return signal / m * peak


def save(name, signal, fade=0.015):
    signal = np.asarray(signal, dtype=np.float64)
    n = int(fade * SR)
    if n > 1:
        signal[:n] *= np.linspace(0, 1, n)
        signal[-n:] *= np.linspace(1, 0, n)
    signal = normalize(signal)
    path = os.path.join(ROOT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((signal * 32767).astype("<i2").tobytes())
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "5", path],
        check=True,
    )
    os.unlink(wav_path)
    print("wrote", path, f"{len(signal) / SR:.2f}s")


def water_cast():
    dur = 1.25
    t = t_axis(dur)
    body = glide(t, 220, 780, 1.6) * 0.24 * envelope(t, 0.05, 2.4)
    air = noise_burst(dur, 0.35, 0.85, 3.4) * 0.35
    sparkle = np.zeros_like(t)
    # staggered crystal touches
    for i, (delay, f) in enumerate(((0.05, 988), (0.2, 1318), (0.34, 1568), (0.5, 1976))):
        local_t = t_axis(0.5)
        tone = chime(local_t, f, decay=6.0) * 0.12
        i0 = int(delay * SR)
        sparkle[i0:i0 + len(tone)] += tone[: len(t) - i0]
    signal = body + air + sparkle
    signal = add_bubbles(signal, 12, (0.05, 0.95), (300, 900))
    return reverb(signal)


def water_summon():
    dur = 1.6
    t = t_axis(dur)
    signal = np.zeros_like(t)
    notes = (523.25, 659.25, 783.99, 1046.5, 1318.5)
    for i, f in enumerate(notes):
        local_t = t_axis(1.0)
        tone = chime(local_t, f, decay=2.6, harmonics=(1.0, 0.5, 0.28, 0.12)) * 0.16
        i0 = int(i * 0.13 * SR)
        signal[i0:i0 + len(tone)] += tone[: len(t) - i0]
    swell = glide(t, 160, 520, 2.2) * 0.12 * envelope(t, 0.25, 2.0)
    shimmer = noise_burst(dur, 0.5, 0.95, 2.2) * 0.12
    signal += swell + shimmer
    signal = add_bubbles(signal, 18, (0.1, 1.4), (500, 1600))
    return reverb(signal)


def water_extinguish():
    dur = 1.4
    t = t_axis(dur)
    hiss = noise_burst(dur, 0.25, 0.9, 2.6) * 0.55
    # fading steam tail
    tail = noise_burst(dur, 0.1, 0.5, 1.2) * 0.2
    signal = hiss + tail
    signal = add_bubbles(signal, 26, (0.05, 1.2), (220, 1000))
    thump = glide(t, 140, 90, 0.7) * 0.18 * envelope(t, 0.01, 6.0)
    return reverb(signal + thump)


def fire_cast():
    dur = 1.15
    t = t_axis(dur)
    roar = noise_burst(dur, 0.04, 0.45, 3.0) * 0.5
    whoosh = glide(t, 90, 260, 1.4) * 0.2 * envelope(t, 0.04, 2.2)
    crackle = np.zeros_like(t)
    for _ in range(30):
        start = rng.uniform(0.02, 0.9)
        i0 = int(start * SR)
        n = int(rng.uniform(0.002, 0.008) * SR)
        if i0 + n < len(t):
            crackle[i0:i0 + n] += rng.normal(0, 1, n) * rng.uniform(0.15, 0.4)
    return reverb(roar + whoosh + crackle)


def spirit_ambient():
    dur = 2.6
    t = t_axis(dur)
    hum = (
        np.sin(2 * np.pi * 330 * t) * 0.12
        + np.sin(2 * np.pi * 495 * t) * 0.06
        + np.sin(2 * np.pi * 660 * t + np.sin(t * 4.0)) * 0.04
    )
    wobble = 1.0 + 0.18 * np.sin(t * 3.1)
    pad = hum * wobble * envelope(t, 0.5, 0.8)
    signal = pad
    for delay, f in ((0.2, 990), (0.85, 1174)):
        local_t = t_axis(1.0)
        tone = chime(local_t, f, decay=3.0) * 0.07
        i0 = int(delay * SR)
        signal[i0:i0 + len(tone)] += tone[: len(t) - i0]
    signal = add_bubbles(signal, 8, (0.1, 2.2), (500, 1200))
    return reverb(signal)


def spirit_hurt():
    dur = 0.55
    t = t_axis(dur)
    cry = glide(t, 900, 420, 0.8) * 0.3 * envelope(t, 0.006, 9.0)
    splash = noise_burst(dur, 0.2, 0.9, 12.0) * 0.28
    signal = add_bubbles(cry + splash, 6, (0.0, 0.25), (600, 1400))
    return reverb(signal, taps=((0.04, 0.18),))


def spirit_death():
    dur = 1.5
    t = t_axis(dur)
    fall = glide(t, 780, 180, 1.7) * 0.28 * envelope(t, 0.01, 2.2)
    shimmer = np.zeros_like(t)
    for i, f in enumerate((1318, 1046, 784, 523)):
        local_t = t_axis(1.0)
        tone = chime(local_t, f, decay=2.4) * 0.07
        i0 = int(i * 0.18 * SR)
        shimmer[i0:i0 + len(tone)] += tone[: len(t) - i0]
    signal = add_bubbles(fall + shimmer, 16, (0.0, 1.2), (300, 1100))
    return reverb(signal)


def spirit_attack():
    dur = 0.5
    t = t_axis(dur)
    slash = noise_burst(dur, 0.4, 0.95, 16.0) * 0.4
    ping = glide(t, 1400, 900, 0.6) * 0.18 * envelope(t, 0.003, 14.0)
    signal = add_bubbles(slash + ping, 5, (0.0, 0.16), (700, 1500))
    return reverb(signal, taps=((0.035, 0.2),))


if __name__ == "__main__":
    save("spell/water_cast", water_cast())
    save("spell/water_summon", water_summon())
    save("spell/water_extinguish", water_extinguish())
    save("spell/fire_cast", fire_cast())
    save("spirit/ambient", spirit_ambient())
    save("spirit/hurt", spirit_hurt())
    save("spirit/death", spirit_death())
    save("spirit/attack", spirit_attack())
