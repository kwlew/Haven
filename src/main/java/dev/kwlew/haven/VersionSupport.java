package dev.kwlew.haven;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The manifest can declare 1.18, but cannot express the required 1.18.2 patch. */
public final class VersionSupport {

    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*");

    private VersionSupport() {}

    public static boolean supports(String bukkitVersion) {
        Matcher match = VERSION.matcher(bukkitVersion);
        if (!match.matches()) {
            return false;
        }
        int major = Integer.parseInt(match.group(1));
        int minor = Integer.parseInt(match.group(2));
        int patch = match.group(3) == null ? 0 : Integer.parseInt(match.group(3));

        return major > 1 || (major == 1 && (minor > 18 || (minor == 18 && patch >= 2)));
    }
}
