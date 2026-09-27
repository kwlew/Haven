package dev.kwlew.haven.update;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Numeric release comparison; prereleases sort below the corresponding stable release. */
record ReleaseVersion(BigInteger major, BigInteger minor, BigInteger patch, boolean prerelease)
        implements Comparable<ReleaseVersion> {

    private static final Pattern FORMAT = Pattern.compile(
            "[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

    static Optional<ReleaseVersion> parse(String value) {
        Matcher match = FORMAT.matcher(value);
        if (!match.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ReleaseVersion(new BigInteger(match.group(1)),
                new BigInteger(match.group(2)),
                match.group(3) == null ? BigInteger.ZERO : new BigInteger(match.group(3)),
                match.group(4) != null));
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        int result = major.compareTo(other.major);
        if (result == 0) result = minor.compareTo(other.minor);
        if (result == 0) result = patch.compareTo(other.patch);
        if (result == 0) result = Boolean.compare(other.prerelease, prerelease);
        return result;
    }
}
