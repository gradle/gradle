/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.wrapper.internal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.GradleException;
import org.gradle.api.resources.TextResource;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.DistributionLocator;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class GradleVersionResolver {

    public static final String LATEST = "latest";
    public static final String NIGHTLY = "nightly";
    public static final String RELEASE_NIGHTLY = "release-nightly";
    public static final String RELEASE_CANDIDATE = "release-candidate";
    public static final String RELEASE_MILESTONE = "release-milestone";

    public static final List<String> PLACE_HOLDERS = Arrays.asList(LATEST, RELEASE_CANDIDATE, RELEASE_MILESTONE, RELEASE_NIGHTLY, NIGHTLY); // order these from most-to-least stable

    private final TextResource latest;
    private final TextResource releaseCandidate;
    private final TextResource releaseMilestone;
    private final TextResource nightly;
    private final TextResource releaseNightly;

    @Nullable
    private GradleVersion gradleVersion;
    private String gradleVersionString = GradleVersion.current().getVersion();


    public GradleVersionResolver(TextResourceFactory textFactory) {
        String versionUrl = DistributionLocator.getBaseUrl() + "/versions";
        this.latest = textFactory.fromUri(versionUrl + "/current");
        this.releaseCandidate = textFactory.fromUri(versionUrl + "/release-candidate");
        this.releaseMilestone = textFactory.fromUri(versionUrl + "/milestone");
        this.nightly = textFactory.fromUri(versionUrl + "/nightly");
        this.releaseNightly = textFactory.fromUri(versionUrl + "/release-nightly");
    }

    public GradleVersion getGradleVersion() {
        if (gradleVersion == null) {
            gradleVersion = GradleVersion.version(resolve(gradleVersionString));
        }
        return gradleVersion;
    }

    public void setGradleVersionString(String gradleVersionString) {
        if (!isPlaceHolder(gradleVersionString)) {
            try {
                this.gradleVersion = GradleVersion.version(gradleVersionString);
            } catch (Exception e) {
                throw new WrapperVersionException("Invalid version specified for argument '--gradle-version'", e);
            }
        }

        if (!this.gradleVersionString.equals(gradleVersionString)) {
            this.gradleVersionString = gradleVersionString;
            this.gradleVersion = null;
        }
    }

    private String resolve(@Nullable String version) {
        if (version == null) {
            return GradleVersion.current().getVersion();
        }
        switch (version) {
            case LATEST:
                return getVersion(latest.asString(), version);
            case NIGHTLY:
                return getVersion(nightly.asString(), version);
            case RELEASE_NIGHTLY:
                return getVersion(releaseNightly.asString(), version);
            case RELEASE_CANDIDATE:
                return getVersion(releaseCandidate.asString(), version);
            case RELEASE_MILESTONE:
                return getVersion(releaseMilestone.asString(), version);
            default:
                return version;
        }
    }

    private static String getVersion(String json, String placeHolder) {
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> map = new Gson().fromJson(json, type);
        String version = map.get("version");
        if (version == null) {
            throw new GradleException("There is currently no version information available for '" + placeHolder + "'.");
        }
        return version;
    }

    private static boolean isPlaceHolder(String version) {
        return PLACE_HOLDERS.contains(version);
    }

    /**
     * This exception is thrown when the wrapper task is run in an attempt to update the wrapper version and
     * an invalid version is specified.
     */
    public static final class WrapperVersionException extends GradleException implements ResolutionProvider {
        public WrapperVersionException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }

        @Override
        public List<String> getResolutions() {
            return Arrays.asList(suggestActualVersion(), suggestDynamicVersions());
        }

        private static String suggestActualVersion() {
            return "Specify a valid Gradle release listed on https://gradle.org/releases/.";
        }

        private static String suggestDynamicVersions() {
            String validStrings = PLACE_HOLDERS.stream()
                .map(s -> String.format("'%s'", s))
                .collect(Collectors.joining(", "));
            return String.format("Use one of the following dynamic version specifications: %s.", validStrings);
        }
    }
}
