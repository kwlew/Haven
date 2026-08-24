package dev.kwlew.haven.globals;

/**
 * Constants that have no home in {@code plugin.yml}.
 * <p>
 * Name, version and authors are deliberately <em>not</em> here - they live in
 * {@code plugin.yml} (templated from {@code gradle.properties} at build time) and are read at
 * runtime through {@code plugin.getPluginMeta()}. Duplicating them as literals meant two sources
 * of truth that drift the first time one is bumped without the other.
 */
public final class BuildINFO {

    /** bStats service id for this plugin. */
    public static final int bStats_ID = 33416;

    public static final String Repo_URL = "https://github.com/kwlew/haven";

    private BuildINFO() {
    }
}
