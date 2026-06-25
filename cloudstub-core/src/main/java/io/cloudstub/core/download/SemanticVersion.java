package io.cloudstub.core.download;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version of the form {@code MAJOR.MINOR.PATCH[-prerelease]}, ordered by Semantic Versioning
 * precedence: numeric major/minor/patch compared field by field, a release ranked above any
 * prerelease of the same major/minor/patch, and dot-separated prerelease identifiers compared with
 * numeric identifiers ordered numerically and below alphanumeric ones.
 */
final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern GRAMMAR =
            Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?");

    private final int major;
    private final int minor;
    private final int patch;
    private final String[] prerelease;

    private SemanticVersion(int major, int minor, int patch, String[] prerelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
    }

    /**
     * @return the parsed version, or {@code null} if {@code text} does not match the grammar
     */
    static SemanticVersion parseOrNull(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = GRAMMAR.matcher(text.trim());
        if (!m.matches()) {
            return null;
        }
        String pre = m.group(4);
        String[] ids = (pre == null || pre.isEmpty()) ? new String[0] : pre.split("\\.");
        return new SemanticVersion(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                ids);
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int c = Integer.compare(major, other.major);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(minor, other.minor);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(patch, other.patch);
        if (c != 0) {
            return c;
        }
        boolean thisRelease = prerelease.length == 0;
        boolean otherRelease = other.prerelease.length == 0;
        if (thisRelease || otherRelease) {
            return Boolean.compare(thisRelease, otherRelease);
        }
        return comparePrerelease(prerelease, other.prerelease);
    }

    private static int comparePrerelease(String[] a, String[] b) {
        int shared = Math.min(a.length, b.length);
        for (int i = 0; i < shared; i++) {
            int c = compareIdentifier(a[i], b[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private static int compareIdentifier(String a, String b) {
        boolean aNumeric = isNumeric(a);
        boolean bNumeric = isNumeric(b);
        if (aNumeric && bNumeric) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        }
        if (aNumeric != bNumeric) {
            return aNumeric ? -1 : 1;
        }
        return a.compareTo(b);
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
