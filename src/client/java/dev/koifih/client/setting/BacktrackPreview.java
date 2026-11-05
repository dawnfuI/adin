package dev.koifih.client.setting;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class BacktrackPreview extends PreviewSetting {
    private final IntSupplier delayMillis;
    private final BooleanSupplier frozen;

    public BacktrackPreview(String id, IntSupplier delayMillis, BooleanSupplier frozen) {
        super(id);
        this.delayMillis = delayMillis;
        this.frozen = frozen;
    }

    public int delayMillis() {
        return delayMillis.getAsInt();
    }

    public boolean frozen() {
        return frozen.getAsBoolean();
    }
}
