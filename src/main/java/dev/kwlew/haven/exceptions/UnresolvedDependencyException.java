package dev.kwlew.haven.exceptions;

public class UnresolvedDependencyException extends RuntimeException {
    public UnresolvedDependencyException(Class<?> message) {
        super(String.valueOf(message));
    }
}
