package dev.koifih.client.event;

import dev.koifih.Adin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EventBus {
    private static final class Handler<E extends Event> {
        private final Class<E> type;
        private final int priority;
        private final Listener<E> listener;
        private volatile boolean cancelled;

        private Handler(Class<E> type, int priority, Listener<E> listener) {
            this.type = type;
            this.priority = priority;
            this.listener = listener;
        }

        @SuppressWarnings("unchecked")
        void accept(Event event) {
            listener.on((E) event);
        }
    }

    private static final Handler<?>[] NONE = new Handler<?>[0];

    private final Map<Class<? extends Event>, List<Handler<?>>> subscribers = new HashMap<>();
    private final Map<Class<? extends Event>, Handler<?>[]> dispatch = new ConcurrentHashMap<>();

    public <E extends Event> Subscription subscribe(Class<E> type, Listener<E> listener) {
        return subscribe(type, Priority.NORMAL, listener);
    }

    public <E extends Event> Subscription subscribe(Class<E> type, int priority, Listener<E> listener) {
        Handler<E> handler = new Handler<>(type, priority, listener);
        synchronized (this) {
            subscribers.computeIfAbsent(type, key -> new ArrayList<>()).add(handler);
            dispatch.clear();
        }
        return () -> {
            handler.cancelled = true;
            synchronized (this) {
                List<Handler<?>> registered = subscribers.get(type);
                if (registered != null && registered.remove(handler)) dispatch.clear();
            }
        };
    }

    public <E extends Event> E post(E event) {
        Handler<?>[] handlers = dispatch.get(event.getClass());
        if (handlers == null) handlers = resolve(event.getClass());
        for (Handler<?> handler : handlers) {
            if (handler.cancelled) continue;
            try {
                handler.accept(event);
            } catch (RuntimeException exception) {
                Adin.LOGGER.error("Listener for {} failed", handler.type.getSimpleName(), exception);
            }
        }
        return event;
    }

    private synchronized Handler<?>[] resolve(Class<? extends Event> type) {
        List<Handler<?>> found = new ArrayList<>();
        subscribers.forEach((registered, handlers) -> {
            if (registered.isAssignableFrom(type)) found.addAll(handlers);
        });
        found.sort((a, b) -> Integer.compare(b.priority, a.priority));
        Handler<?>[] resolved = found.toArray(NONE);
        dispatch.put(type, resolved);
        return resolved;
    }
}
