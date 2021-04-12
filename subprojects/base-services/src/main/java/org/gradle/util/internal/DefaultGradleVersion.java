/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.util.internal;


import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GradleVersion;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.gradle.internal.IoActions.uncheckedClose;

public final class DefaultGradleVersion extends GradleVersion {
    private static final Pattern VERSION_PATTERN = Pattern.compile("((\\d+)(\\.\\d+)+)(-(\\p{Alpha}+)-(\\w+))?(-(SNAPSHOT|\\d{14}([-+]\\d{4})?))?");
    private static final int STAGE_MILESTONE = 0;
    private static final int STAGE_UNKNOWN = 1;
    private static final int STAGE_PREVIEW = 2;
    private static final int STAGE_RC = 3;

    private final String version;
    private final int majorPart;
    private final String buildTime;
    private final String commitId;
    private final Long snapshot;
    private final String versionPart;
    private final Stage stage;
    private static final DefaultGradleVersion CURRENT;

    public static final String RESOURCE_NAME = "/org/gradle/build-receipt.properties";
    public static final String VERSION_OVERRIDE_VAR = "GRADLE_VERSION_OVERRIDE";
    public static final String VERSION_NUMBER_PROPERTY = "versionNumber";

    static {
        URL resource = DefaultGradleVersion.class.getResource(RESOURCE_NAME);
        if (resource == null) {
            throw new GradleException(format("Resource '%s' not found.", RESOURCE_NAME));
        }

        InputStream inputStream = null;
        try {
            URLConnection connection = resource.openConnection();
            connection.setUseCaches(false);
            inputStream = connection.getInputStream();
            Properties properties = new Properties();
            properties.load(inputStream);

            String version = properties.get(VERSION_NUMBER_PROPERTY).toString();

            // We allow the gradle version to be overridden for tests that are sensitive
            // to the version and need to test with various different version patterns.
            // We use an env variable because these are easy to set on daemon startup,
            // whereas system properties are scrubbed at daemon startup.
            String overrideVersion = System.getenv(VERSION_OVERRIDE_VAR);
            if (overrideVersion != null) {
                version = overrideVersion;
            }

            String buildTimestamp = properties.get("buildTimestampIso").toString();
            String commitId = properties.get("commitId").toString();

            CURRENT = new DefaultGradleVersion(version, "unknown".equals(buildTimestamp) ? null : buildTimestamp, commitId);
        } catch (Exception e) {
            throw new GradleException(format("Could not load version details from resource '%s'.", resource), e);
        } finally {
            if (inputStream != null) {
                uncheckedClose(inputStream);
            }
        }
    }

    public static DefaultGradleVersion current() {
        return CURRENT;
    }

    /**
     * Parses the given string into a GradleVersion.
     *
     * @throws IllegalArgumentException On unrecognized version string.
     */
    public static DefaultGradleVersion version(String version) throws IllegalArgumentException {
        return new DefaultGradleVersion(version, null, null);
    }

    private DefaultGradleVersion(String version, String buildTime, String commitId) {
        this.version = version;
        this.buildTime = buildTime;
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(format("'%s' is not a valid Gradle version string (examples: '1.0', '1.0-rc-1')", version));
        }

        versionPart = matcher.group(1);
        majorPart = Integer.parseInt(matcher.group(2), 10);

        this.commitId = setOrParseCommitId(commitId, matcher);
        this.stage = parseStage(matcher);
        this.snapshot = parseSnapshot(matcher);
    }

    private Long parseSnapshot(Matcher matcher) {
        if ("snapshot".equals(matcher.group(5)) || isCommitVersion(matcher)) {
            return 0L;
        } else if (matcher.group(8) == null) {
            return null;
        } else if ("SNAPSHOT".equals(matcher.group(8))) {
            return 0L;
        } else {
            try {
                if (matcher.group(9) != null) {
                    return new SimpleDateFormat("yyyyMMddHHmmssZ").parse(matcher.group(8)).getTime();
                } else {
                    SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return format.parse(matcher.group(8)).getTime();
                }
            } catch (ParseException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private Stage parseStage(Matcher matcher) {
        if (matcher.group(4) == null || isCommitVersion(matcher)) {
            return null;
        } else if (isStage("milestone", matcher)) {
            return Stage.from(STAGE_MILESTONE, matcher.group(6));
        } else if (isStage("preview", matcher)) {
            return Stage.from(STAGE_PREVIEW, matcher.group(6));
        } else if (isStage("rc", matcher)) {
            return Stage.from(STAGE_RC, matcher.group(6));
        } else {
            return Stage.from(STAGE_UNKNOWN, matcher.group(6));
        }
    }

    private boolean isCommitVersion(Matcher matcher) {
        return "commit".equals(matcher.group(5));
    }

    private boolean isStage(String stage, Matcher matcher) {
        return stage.equals(matcher.group(5));
    }

    private String setOrParseCommitId(String commitId, Matcher matcher) {
        if (commitId != null || !isCommitVersion(matcher)) {
            return commitId;
        } else {
            return matcher.group(6);
        }
    }

    @Override
    public String toString() {
        return "Gradle " + version;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    @Deprecated
    public String getBuildTime() {
        return getBuildTimestamp();
    }

    public String getBuildTimestamp() {
        return buildTime;
    }

    @Override
    @Deprecated
    public String getRevision() {
        return getGitRevision();
    }

    public String getGitRevision() {
        return commitId;
    }

    @Override
    public boolean isSnapshot() {
        return snapshot != null;
    }

    public GradleVersion getBaseVersion() {
        if (stage == null && snapshot == null) {
            return this;
        }
        return version(versionPart);
    }

    @Override
    @Deprecated
    public GradleVersion getNextMajor() {
        return getNextMajorVersion();
    }

    public DefaultGradleVersion getNextMajorVersion() {
        return version((majorPart + 1) + ".0");
    }

    @Override
    public int compareTo(GradleVersion gv) {
        if (!(gv instanceof DefaultGradleVersion)) {
            throw new RuntimeException("Unexpected GradleVersion subclass: " + gv.getClass().getCanonicalName());
        }

        DefaultGradleVersion gradleVersion = (DefaultGradleVersion) gv;

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

        Long thisSnapshot = snapshot == null ? Long.MAX_VALUE : snapshot;
        Long theirSnapshot = gradleVersion.snapshot == null ? Long.MAX_VALUE : gradleVersion.snapshot;

        if (thisSnapshot.equals(theirSnapshot)) {
            return version.compareTo(gradleVersion.version);
        } else {
            return thisSnapshot.compareTo(theirSnapshot);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        DefaultGradleVersion other = (DefaultGradleVersion) o;
        return version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    @Deprecated
    public boolean isValid() {
        return versionPart != null;
    }

    static final class Stage implements Comparable<Stage> {
        final int stage;
        final int number;
        final Character patchNo;

        private Stage(int stage, int number, Character patchNo) {
            this.stage = stage;
            this.number = number;
            this.patchNo = patchNo;
        }

        static Stage from(int stage, String stageString) {
            Matcher m = Pattern.compile("(\\d+)([a-z])?").matcher(stageString);
            int number;
            if (m.matches()) {
                number = Integer.parseInt(m.group(1));
            } else {
                return null;
            }

            if (m.groupCount() == 2 && m.group(2) != null) {
                return new Stage(stage, number, m.group(2).charAt(0));
            } else {
                return new Stage(stage, number, '_');
            }
        }

        @Override
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
