package dev.koifih.client.ui;

public final class Scroller {
    private final Transition shown;
    private float target;

    public Scroller(int millis) {
        shown = new Transition(0f, millis);
    }

    public void reset() {
        target = 0f;
        shown.snap(0f);
    }

    public void top() {
        target = 0f;
        shown.set(0f);
    }

    public void scroll(double amount, float max) {
        target = Math.clamp((float) (target + amount), 0f, max);
        shown.set(target);
    }

    public void clamp(float max) {
        scroll(0, max);
    }

    public float target() {
        return target;
    }

    public float shown() {
        return shown.value();
    }
}
