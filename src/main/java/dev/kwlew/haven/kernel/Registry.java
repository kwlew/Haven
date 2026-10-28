package dev.kwlew.haven.kernel;

import dev.kwlew.haven.exceptions.CircularDependencyException;
import dev.kwlew.haven.exceptions.UnresolvedDependencyException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Minimal reflection-based DI container. Not thread-safe: used only from {@link Bootstrap} on the main thread
 * during {@code onEnable}, then {@link #seal() sealed}. {@link #getAll()} yields each instance once in creation
 * order (a valid start order; its reverse a valid shutdown order), even when it is bound under several keys.
 */
public class Registry {

    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();
    private final List<Object> ordered = new ArrayList<>();
    private final Set<Object> tracked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Deque<Class<?>> resolutionStack = new ArrayDeque<>();
    private boolean sealed;

    public <T> void register(Class<T> type, T instance) {
        instances.put(type, instance);
        track(instance);
    }

    private void track(Object instance) {
        if (instance != null && tracked.add(instance)) {
            ordered.add(instance);
        }
    }

    public void seal() {
        this.sealed = true;
    }

    public <T> T resolve(Class<T> type) {
        Object existing = instances.get(type);
        if (existing != null) {
            return type.cast(existing);
        }

        if (sealed) {
            throw new IllegalStateException("Registry is sealed; cannot construct " + type.getName()
                    + " after bootstrap. Inject it during bootstrap instead.");
        }

        if (notConstructable(type)) {
            throw new RuntimeException("No registered instance for non-constructable type " + type.getName());
        }

        if (resolutionStack.contains(type)) {
            throw new CircularDependencyException(formatCycle(resolutionStack, type));
        }

        try {
            resolutionStack.addLast(type);
            Constructor<?> constructor = selectConstructor(type);
            Object[] params = Arrays.stream(constructor.getParameterTypes())
                    .map(this::resolveDependency)
                    .toArray();

            constructor.setAccessible(true);

            T instance = type.cast(constructor.newInstance(params));
            instances.put(type, instance);
            track(instance);

            return instance;

        } catch (CircularDependencyException | UnresolvedDependencyException e) {
            // Terminal error from a nested resolve; don't wrap it per stack frame.
            throw e;
        } catch (InvocationTargetException e) {
            // Surface the constructor's own cause, not the reflection wrapper.
            throw new RuntimeException("Failed to create " + type.getName(), e.getCause());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create " + type.getName(), e);
        } finally {
            if (!resolutionStack.isEmpty() && resolutionStack.peekLast() == type) {
                resolutionStack.removeLast();
            }
        }
    }

    private Constructor<?> selectConstructor(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();

        if (constructors.length == 0) {
            throw new RuntimeException("No constructor found for " + type.getName());
        }

        Constructor<?> injectConstructor = null;
        for (Constructor<?> constructor : constructors) {
            if (!constructor.isAnnotationPresent(Inject.class)) {
                continue;
            }

            if (injectConstructor != null) {
                throw new RuntimeException("Multiple @Inject constructors found for " + type.getName());
            }

            injectConstructor = constructor;
        }

        if (injectConstructor != null) {
            return injectConstructor;
        }

        return Arrays.stream(constructors)
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow(() -> new RuntimeException("No constructor found for " + type.getName()));
    }

    public List<Object> getAll() {
        return List.copyOf(ordered);
    }

    public List<Object> getAllReversed() {
        List<Object> reversed = new ArrayList<>(ordered);
        Collections.reverse(reversed);
        return Collections.unmodifiableList(reversed);
    }

    private Object resolveDependency(Class<?> dependencyType) {
        Object existing = instances.get(dependencyType);
        if (existing != null) {
            return existing;
        }

        if (notConstructable(dependencyType)) {
            throw new UnresolvedDependencyException(dependencyType);
        }

        return resolve(dependencyType);
    }

    private boolean notConstructable(Class<?> type) {
        return type.isInterface() || Modifier.isAbstract(type.getModifiers());
    }

    /** The instance ends up under both keys; {@link #track} keeps it out of {@link #ordered} twice. */
    public <T> void bind(Class<T> abstraction, Class<? extends T> implementation) {
        instances.put(abstraction, resolve(implementation));
    }

    private String formatCycle(Deque<Class<?>> stack, Class<?> repeatedType) {
        StringBuilder cycle = new StringBuilder();
        boolean append = false;

        for (Class<?> type : stack) {
            if (type == repeatedType) {
                append = true;
            }

            if (append) {
                if (!cycle.isEmpty()) {
                    cycle.append(" -> ");
                }
                cycle.append(type.getSimpleName());
            }
        }

        if (!cycle.isEmpty()) {
            cycle.append(" -> ");
        }
        cycle.append(repeatedType.getSimpleName());

        return cycle.toString();
    }
}