package dev.kwlew.haven.sound;

/**
 * The points at which Haven plays a sound. The {@link #path()} is the key under {@code sounds:}
 * in {@code config.yml}, so adding a value here means adding a matching block there.
 */
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
