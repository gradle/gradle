package org.gradle.integtests.fixtures.executer;

import org.gradle.api.JavaVersion;
import org.gradle.util.GradleVersion;

public class GradleJavaCompatibility {
    /**
     * Returns true if the given Gradle version is compatible with the given Java version.
     */
    public static boolean isGradleCompatibleWithJava(GradleVersion version, JavaVersion javaVersion) {
        // 0.9-rc-1 was broken for Java 5
        if (isVersion(version, "0.9-rc-1") && javaVersion == JavaVersion.VERSION_1_5) {
            return false;
        }

        if (isSameOrOlder(version, "1.0")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_5) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_7) <= 0;
        }

        // 1.x works on Java 5 - 8
        if (isSameOrOlder(version, "1.12")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_5) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_8) <= 0;
        }

        // 2.x and 3.0-milestone-1 work on Java 6 - 8
        if (isSameOrOlder(version, "3.0-milestone-1")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_6) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_8) <= 0;
        }

        // 3.x - 4.6 works on Java 7 - 8
        if (isSameOrOlder(version, "4.6")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_7) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_8) <= 0;
        }

        if (isSameOrOlder(version, "4.11")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_7) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_10) <= 0;
        }

        // 5.4 officially added support for JDK 12, but it worked before then.
        if (isSameOrOlder(version, "5.7")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_12) <= 0;
        }

        if (isSameOrOlder(version, "6.0")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_13) <= 0;
        }

        // 6.7 added official support for JDK15
        if (isSameOrOlder(version, "6.6.1")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_14) <= 0;
        }

        // 7.0 added official support for JDK16
        // milestone 2 was published with Groovy 3 upgrade and without asm upgrade yet
        // subsequent milestones and RCs will support JDK16
        if (isSameOrOlder(version, "7.0-milestone-2")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_15) <= 0;
        }

        // 7.3 added JDK 17 support
        if (isSameOrOlder(version, "7.2")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_16) <= 0;
        }

        // 7.5 added JDK 18 support
        if (isSameOrOlder(version, "7.4.2")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_17) <= 0;
        }

        // 7.6 added JDK 19 support
        if (isSameOrOlder(version, "7.5.1")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_18) <= 0;
        }

        // 8.3 added JDK 20 support
        if (isSameOrOlder(version, "8.2.1")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_19) <= 0;
        }

        // 8.5 added JDK 21 support
        if (isSameOrOlder(version, "8.4")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_20) <= 0;
        }

        // 8.8 added JDK 22 support
        if (isSameOrOlder(version, "8.7")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_21) <= 0;
        }

        // 8.10 added JDK 23 support
        if (isSameOrOlder(version, "8.9")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_22) <= 0;
        }

        // 8.14 added JDK 24 support
        if (isSameOrOlder(version, "8.13")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_23) <= 0;
        }

        // 9.0+ requires Java 17
        if (isSameOrOlder(version, "8.14")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_24) <= 0;
        }

        return javaVersion.compareTo(JavaVersion.VERSION_17) >= 0 && maybeEnforceHighestVersion(javaVersion, JavaVersion.VERSION_24);
    }

    /**
     * Returns true if the given java version is less than the given highest version bound.  Always returns
     * true if the highest version check is disabled via system property.
     */
    private static boolean maybeEnforceHighestVersion(JavaVersion javaVersion, JavaVersion highestVersion) {
        String DISABLE_HIGHEST_JAVA_VERSION = "org.gradle.java.version.disableHighest";
        boolean disableHighest = System.getProperty(DISABLE_HIGHEST_JAVA_VERSION) != null;
        return disableHighest || javaVersion.compareTo(highestVersion) <= 0;
    }

    private static boolean isSameOrOlder(GradleVersion version, String otherVersion) {
        return isVersion(version, otherVersion) || version.compareTo(GradleVersion.version(otherVersion)) <= 0;
    }

    private static boolean isVersion(GradleVersion version, String otherVersionString) {
        GradleVersion otherVersion = GradleVersion.version(otherVersionString);
        return version.compareTo(otherVersion) == 0 || (version.isSnapshot() && version.getBaseVersion().equals(otherVersion.getBaseVersion()));
    }
}
