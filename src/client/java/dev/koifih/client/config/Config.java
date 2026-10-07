package dev.koifih.client.config;

public final class Config extends State {
    public enum Scope { COLORS, SETTINGS, BOTH }
    public enum Visibility { PRIVATE, PUBLIC }

    public String id = "";
    public String name = "";
    public String description = "";
    public String author = "";
    public Scope scope = Scope.BOTH;
    public Visibility visibility = Visibility.PRIVATE;
    public long created;

    public boolean hasColors() {
        return scope != Scope.SETTINGS && colors != null;
    }

    public boolean hasSettings() {
        return scope != Scope.COLORS && modules != null;
    }
}
