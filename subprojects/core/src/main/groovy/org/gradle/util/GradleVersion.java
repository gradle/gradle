/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.util;

import groovy.lang.GroovySystem;
import org.apache.ivy.Ivy;
import org.apache.tools.ant.Main;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleVersion implements Comparable<GradleVersion> {
    public static final String URL = "http://www.gradle.org";
    private static final Pattern VERSION_PATTERN = Pattern.compile("((\\d+)(\\.\\d+)+)(-(\\p{Alpha}+)-(\\d+[a-z]?))?(-(\\d{14}([-+]\\d{4})?))?");
    private static final int STAGE_MILESTONE = 0;

    private final String version;
    private final int majorPart;
    private final String buildTime;
    private final String commitId;
    private final String buildNumber;
    private final Long snapshot;
    private final String versionPart;
    private final Stage stage;
    private static final GradleVersion CURRENT;

    public static final String RESOURCE_NAME = "/org/gradle/build-receipt.properties";

    // TODO - get rid of this static initialiser nonsense
    static {
        URL resource = GradleVersion.class.getResource(RESOURCE_NAME);

        InputStream inputStream = null;
        try {
            URLConnection connection = resource.openConnection();
            inputStream = connection.getInputStream();
            Properties properties = new Properties();
            properties.load(inputStream);

            String version = properties.get("versionNumber").toString();
            String buildTimestamp = properties.get("buildTimestamp").toString();
            String buildNumber = properties.get("buildNumber").toString();
            String commitId = properties.get("commitId").toString();
            Date buildTime = new SimpleDateFormat("yyyyMMddHHmmssZ").parse(buildTimestamp);

            CURRENT = new GradleVersion(version, buildTime, buildNumber, commitId);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not load version details from resource '%s'.", resource), e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }


    public static GradleVersion current() {
        return CURRENT;
    }

    /**
     * Parses the given string into a GradleVersion.
     *
     * @throws IllegalArgumentException On unrecognized version string.
     */
    public static GradleVersion version(String version) throws IllegalArgumentException {
        return new GradleVersion(version, null, null, null);
    }

    private GradleVersion(String version, Date buildTime, String buildNumber, String commitId) {
        this.version = version;
        this.buildNumber = buildNumber;
        this.commitId = commitId;
        this.buildTime = buildTime == null ? null : formatBuildTime(buildTime);
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format("'%s' is not a valid Gradle version string (examples: '1.0', '1.0-rc-1')", version));
        }

        versionPart = matcher.group(1);
        majorPart = Integer.parseInt(matcher.group(2), 10);

        if (matcher.group(4) != null) {
            int stageNumber;
            if (matcher.group(5).equals("milestone")) {
                stageNumber = STAGE_MILESTONE;
            } else if (matcher.group(5).equals("preview")) {
                stageNumber = 2;
            } else if (matcher.group(5).equals("rc")) {
                stageNumber = 3;
            } else {
                stageNumber = 1;
            }
            String stageString = matcher.group(6);
            stage = new Stage(stageNumber, stageString);
        } else {
            stage = null;
        }

        if (matcher.group(8) != null) {
            try {
                if (matcher.group(9) != null) {
                    snapshot = new SimpleDateFormat("yyyyMMddHHmmssZ").parse(matcher.group(8)).getTime();
                } else {
                    SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                    snapshot = format.parse(matcher.group(8)).getTime();
                }
            } catch (ParseException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } else {
            snapshot = null;
        }
    }

    private String formatBuildTime(Date buildTime) {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(buildTime);
    }

    @Override
    public String toString() {
        return String.format("Gradle %s", version);
    }

    public String getVersion() {
        return version;
    }

    public String getBuildTime() {
        return buildTime;
    }

    public boolean isSnapshot() {
        return snapshot != null;
    }

    /**
     * The base version of this version. For pre-release versions, this is the target version.
     *
     * For example, the version base of '1.2-rc-1' is '1.2'.
     *
     * @return The version base
     */
    public GradleVersion getBaseVersion() {
        if (stage == null && snapshot == null) {
            return this;
        }
        if (stage != null && stage.stage == STAGE_MILESTONE) {
            return version(versionPart + "-milestone-" + stage.number);
        }
        return version(versionPart);
    }

    public GradleVersion getNextMajor() {
        if (stage != null && stage.stage == STAGE_MILESTONE) {
            return version(majorPart + ".0");
        }
        return version((majorPart + 1) + ".0");
    }

    public int compareTo(GradleVersion gradleVersion) {
        String[] majorVersionParts = versionPart.split("\\.");
        String[] otherMajorVersionParts = gradleVersion.versionPart.split("\\.");

        for (int i = 0; i < majorVersionParts.length && i < otherMajorVersionParts.length; i++) {
            int part = Integer.parseInt(majorVersionParts[i]);
            int otherPart = Integer.parseInt(otherMajorVersionParts[i]);

            if (part > otherPart) {
                return 1;
            }
            if (otherPart > part) {
                return -1;
            }
        }
        if (majorVersionParts.length > otherMajorVersionParts.length) {
            return 1;
        }
        if (majorVersionParts.length < otherMajorVersionParts.length) {
            return -1;
        }

        if (stage != null && gradleVersion.stage != null) {
            int diff = stage.compareTo(gradleVersion.stage);
            if (diff != 0) {
                return diff;
            }
        }
        if (stage == null && gradleVersion.stage != null) {
            return 1;
        }
        if (stage != null && gradleVersion.stage == null) {
            return -1;
        }

        if (snapshot != null && gradleVersion.snapshot != null) {
            return snapshot.compareTo(gradleVersion.snapshot);
        }
        if (snapshot == null && gradleVersion.snapshot != null) {
            return 1;
        }
        if (snapshot != null && gradleVersion.snapshot == null) {
            return -1;
        }

        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        GradleVersion other = (GradleVersion) o;
        return version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    public String prettyPrint() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n------------------------------------------------------------\nGradle ");
        sb.append(getVersion());
        sb.append("\n------------------------------------------------------------\n\nBuild time:   ");
        sb.append(getBuildTime());
        sb.append("\nBuild number: ");
        sb.append(buildNumber);
        sb.append("\nRevision:     ");
        sb.append(commitId);
        sb.append("\n\nGroovy:       ");
        sb.append(GroovySystem.getVersion());
        sb.append("\nAnt:          ");
        sb.append(Main.getAntVersion());
        sb.append("\nIvy:          ");
        sb.append(Ivy.getIvyVersion());
        sb.append("\nJVM:          ");
        sb.append(Jvm.current());
        sb.append("\nOS:           ");
        sb.append(OperatingSystem.current());
        sb.append("\n");
        return sb.toString();
    }

    public boolean isValid() {
        return versionPart != null;
    }

    static final class Stage implements Comparable<Stage> {
        final int stage;
        final int number;
        final Character patchNo;

        Stage(int stage, String number) {
            this.stage = stage;
            Matcher m = Pattern.compile("(\\d+)([a-z])?").matcher(number);
            try {
                m.matches();
                this.number = Integer.parseInt(m.group(1));
            } catch (Exception e) {
                throw new RuntimeException("Invalid stage small number: " + number, e);
            }

            if (m.groupCount() == 2 && m.group(2) != null) {
                this.patchNo = m.group(2).charAt(0);
            } else {
                this.patchNo = '_';
            }
        }

        public int compareTo(Stage other) {
            if (stage > other.stage) {
                return 1;
            }
            if (stage < other.stage) {
                return -1;
            }
            if (number > other.number) {
                return 1;
            }
            if (number < other.number) {
                return -1;
            }
            if (patchNo > other.patchNo) {
                return 1;
            }
            if (patchNo < other.patchNo) {
                return -1;
            }
            return 0;
        }
    }
}
