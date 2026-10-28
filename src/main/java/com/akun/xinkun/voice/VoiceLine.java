package com.akun.xinkun.voice;

import net.minecraft.sounds.SoundEvent;

public record VoiceLine(SoundEvent sound, String text, int bubbleTicks) {
}
