package dev.koifih.client.rotation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RotationMode {
    public static final String[] NAMES = {"Manual", "Auto", "Silent"};
    public static final int MANUAL = 0;
    public static final int AUTO = 1;
    public static final int SILENT = 2;

    private static final RotationConfig VISIBLE = RotationConfig.visible(0f, Smoothing.EASE_OUT_CUBIC);
    private static final RotationConfig HIDDEN = RotationConfig.silent(0f, Smoothing.EASE_OUT_CUBIC);

    public static RotationConfig config(int mode) {
        return mode == AUTO ? VISIBLE : HIDDEN;
    }
}
