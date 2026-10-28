package dev.kwlew.haven.managers.sound;

/** {@link #path()} is the key under {@code sounds:} in {@code config.yml}. */
public enum HavenSound {

    HOME_SET("home-set"),
    HOME_DELETED("home-deleted"),
    WARMUP_TICK("warmup-tick"),
    TELEPORT_SUCCESS("teleport-success"),
    TELEPORT_CANCELLED("teleport-cancelled"),
    DENIED("denied");

    private final String path;

    HavenSound(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
