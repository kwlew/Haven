// dev/kwlew/kernel/Registry.java
package dev.kwlew.haven.kernel;

import dev.kwlew.haven.exceptions.CircularDependencyException;
import dev.kwlew.haven.exceptions.UnresolvedDependencyException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * A minimal reflection-based dependency injection container.
 * <p>
 * Not thread-safe: {@link #register}, {@link #resolve}, and {@link #bind} are only ever called
 * from {@link Bootstrap} during {@code onEnable}, on the main server thread. Concurrent calls
 * (e.g. from an async task resolving a not-yet-constructed type) are unsupported. Once
 * {@link #seal()} has been called no further types can be constructed, which keeps late
 * off-thread callers (login listeners run on Netty threads) from mutating {@link #instances}.
 * <p>
 * A single instance may be reachable under several keys - {@link #bind} deliberately stores one
 * object under both an abstraction and its implementation. {@link #instances} is therefore only a
 * lookup table; the canonical iteration order lives in {@link #ordered}, which holds each distinct
 * instance exactly once at its <em>creation</em> position. That is the only defensible ordering:
 * an object is always constructed after its dependencies, so creation order is a valid
 * {@code init()}/{@code start()} sequence and its exact reverse a valid {@code shutdown()} order.
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

    /**
     * Records an instance at its creation position, ignoring instances already seen so an object
     * reachable under several keys is still iterated exactly once by {@link #getAll()}.
     */
    private void track(Object instance) {
        if (instance != null && tracked.add(instance)) {
            ordered.add(instance);
        }
    }

    /**
     * Closes the registry to further construction. Lookups of already-registered types keep
     * working; anything that would create a new instance throws instead.
     */
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
            // Already a meaningful, terminal error from a nested resolveDependency() call -
            // propagate it as-is instead of burying it under a "Failed to create" wrapper
            // per stack frame.
            throw e;
        } catch (InvocationTargetException e) {
            // The constructor itself threw; surface its cause directly instead of the
            // reflection-only wrapper, so callers see what actually broke.
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

    /**
     * Returns every distinct instance once, in creation order. An instance registered under
     * several keys (see {@link #bind}) still appears exactly once.
     */
    public List<Object> getAll() {
        return List.copyOf(ordered);
    }

    /**
     * Returns all instances in reverse creation order, for safe teardown
     * (dependents shut down before the dependencies they were built from).
     */
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

    /**
     * Makes {@code abstraction} resolve to the (single) instance of {@code implementation}. The
     * instance ends up under both keys on purpose; {@link #track} keeps it out of {@link #ordered}
     * a second time so its lifecycle hooks still run exactly once.
     */
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