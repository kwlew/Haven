package dev.kwlew.haven.kernel;

public interface LifecycleComponent {

    default void init() {}
    default void start() {}
    default void shutdown() {}
}