package dev.koifih.client.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import com.mojang.blaze3d.platform.InputConstants;
import dev.koifih.client.AdinClient;
import dev.koifih.client.event.Event;
import dev.koifih.client.event.Listener;
import dev.koifih.client.event.Priority;
import dev.koifih.client.event.Subscription;
import dev.koifih.client.event.events.ModuleToggleEvent;
import dev.koifih.client.setting.Setting;
import dev.koifih.client.setting.SettingGroup;
import dev.koifih.client.util.Lang;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@Accessors(fluent = true)
public abstract class Module {
    public static final Minecraft mc = Minecraft.getInstance();
    @Getter
    private final String id;
    @Getter
    Category category;
    @Getter
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean enabled;
    @Getter
    private InputConstants.Key key = InputConstants.UNKNOWN;
    @Getter
    private boolean hold;
    private boolean keyWasDown;
    private final Lang.Localized name = new Lang.Localized(() -> Lang.getOrDefault("module." + id(), Lang.capitalize(id())));
    private final Lang.Localized description = new Lang.Localized(() -> Lang.getOrDefault("module." + id() + ".description", ""));

    public String name() {
        return name.get();
    }

    public String info() {
        return "";
    }

    public String description() {
        return description.get();
    }

    protected <S extends Setting<?>> S add(S setting) {
        for (Setting<?> existing : settings) {
            if (existing.id().equals(setting.id())) {
                throw new IllegalArgumentException("Duplicate setting id: " + id + "." + setting.id());
            }
        }
        setting.attach(id);
        settings.add(setting);
        return setting;
    }

    protected <G extends SettingGroup> G add(G group) {
        for (Setting<?> setting : group.settings()) add(setting);
        return group;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            for (Subscription subscription : subscriptions) subscription.cancel();
            subscriptions.clear();
            onDisable();
        }
        AdinClient.EVENTS.post(new ModuleToggleEvent(this, enabled));
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setKey(InputConstants.Key key) {
        this.key = key == null ? InputConstants.UNKNOWN : key;
    }

    public void setHold(boolean hold) {
        this.hold = hold;
    }

    public boolean activatable() {
        return false;
    }

    protected void onActivate() {
    }

    protected void onHold() {
    }

    protected void onRelease() {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected <E extends Event> void listen(Class<E> type, Listener<E> listener) {
        listen(type, Priority.NORMAL, listener);
    }

    protected <E extends Event> void listen(Class<E> type, int priority, Listener<E> listener) {
        subscriptions.add(AdinClient.EVENTS.subscribe(type, priority, listener));
    }

    void tickKeybind(Minecraft client) {
        boolean down = key != InputConstants.UNKNOWN && client.gui.screen() == null && isKeyDown(client.getWindow().handle(), key);
        if (activatable()) {
            if (enabled) {
                if (down && !keyWasDown) onActivate();
                if (down) onHold();
                if (!down && keyWasDown) onRelease();
            }
        } else if (hold) {
            if (down != keyWasDown) setEnabled(down);
        } else if (down && !keyWasDown) {
            toggle();
        }
        keyWasDown = down;
    }

    protected static boolean isKeyDown(long window, InputConstants.Key key) {
        return switch (key.getType()) {
            case KEYSYM -> GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}
