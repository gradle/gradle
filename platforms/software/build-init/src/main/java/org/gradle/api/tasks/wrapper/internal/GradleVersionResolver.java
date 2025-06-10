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
import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.DistributionLocator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class GradleVersionResolver {

    private final TextResourceFactory textResourceFactory;
    @Nullable
    private GradleVersion gradleVersion;
    private GradleVersionRequest gradleVersionRequest = new GradleVersionRequest(GradleVersion.current());

    public GradleVersionResolver(TextResourceFactory textResourceFactory) {
        this.textResourceFactory = textResourceFactory;
    }

    public GradleVersion getGradleVersion() {
        if (gradleVersion == null) {
            gradleVersion = resolve();
        }
        return gradleVersion;
    }

    public void setGradleVersionRequest(String request) {
        GradleVersionRequest gradleVersionRequest = new GradleVersionRequest(request);
        if (gradleVersionRequest.requestType == RequestType.VERSION) {
            this.gradleVersion = parseVersionString(request);
        } else if (!Objects.equals(this.gradleVersionRequest, gradleVersionRequest)) {
            this.gradleVersion = null;
            this.gradleVersionRequest = gradleVersionRequest;
        }
    }

    private GradleVersion resolve() {
        switch (gradleVersionRequest.requestType) {
            case DYNAMIC_VERSION:
                String version = getSingleVersion(gradleVersionRequest.dynamicVersion);
                return GradleVersion.version(version);
            case SEMANTIC_VERSION:
                return resolveSemanticVersion(gradleVersionRequest.majorVersion, gradleVersionRequest.minorVersion);
            case VERSION:
                return GradleVersion.version(gradleVersionRequest.request);
            default:
                throw new IllegalArgumentException("Unknown request type: " + gradleVersionRequest.requestType);
        }
    }

    private GradleVersion resolveSemanticVersion(Integer majorVersion, @Nullable Integer minorVersion) {
        Stream<GradleVersion> versions = getVersionsList(majorVersion.toString())
            .stream()
            .map(v -> {
                try {
                    return GradleVersion.version(v);
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .filter(v -> v.isFinal() && v.getMajorVersion() == majorVersion);

        if (minorVersion == null) {
            return versions.max(GradleVersion::compareTo).orElseThrow(() ->
                new WrapperVersionException("Invalid version specified for argument '--gradle-version': no final version found for major version " + majorVersion, null)
            );
        } else {
            return versions
                .filter(v -> getMinorVersion(v) == minorVersion)
                .max(GradleVersion::compareTo).orElseThrow(() ->
                    new WrapperVersionException("Invalid version specified for argument '--gradle-version': no final version found for version " + majorVersion + "." + minorVersion, null)
                );
        }
    }

    private static int getMinorVersion(GradleVersion version) {
        String[] versionParts = version.getBaseVersion().getVersion().split("\\.");
        if (versionParts.length > 1) {
            return Integer.parseInt(versionParts[1]);
        } else {
            return 0;
        }
    }

    private String getApiEndpoint(String request) {
        return DistributionLocator.getBaseUrl() + "/versions/" + request;
    }

    private String getSingleVersion(DynamicVersion dynamicVersion) {
        try {
            return getVersion(textResourceFactory.fromUri(getApiEndpoint(dynamicVersion.urlSuffix)).asString(), dynamicVersion.name);
        } catch (MissingResourceException e) {
            // swallowing the original exception to provide a more user-friendly message
            throw new WrapperVersionException("Unable to resolve Gradle version for '" + dynamicVersion.name + "'.", null);
        }
    }

    private List<String> getVersionsList(String majorVersion) {
        try {
            return getVersions(textResourceFactory.fromUri(getApiEndpoint(majorVersion)).asString());
        } catch (MissingResourceException e) {
            // swallowing the original exception to provide a more user-friendly message
            throw new WrapperVersionException("Unable to resolve list of Gradle versions for '" + majorVersion + "'.", null);
        }
    }

    static String getVersion(String json, String request) {
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> map = new Gson().fromJson(json, type);
        String version = map.get("version");
        if (version == null) {
            throw new GradleException("There is currently no version information available for '" + request + "'.");
        }
        return version;
    }

    static List<String> getVersions(String json) {
        Type type = new TypeToken<List<Map<String, String>>>() {}.getType();
        List<Map<String, String>> map = new Gson().fromJson(json, type);
        return map.stream()
            .map(m -> m.get("version"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static GradleVersion parseVersionString(String gradleVersionString) {
        try {
            return GradleVersion.version(gradleVersionString);
        } catch (Exception e) {
            throw new WrapperVersionException("Invalid version specified for argument '--gradle-version'", e);
        }
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
            String validStrings = Arrays.stream(DynamicVersion.values())
                .map(dv -> dv.name)
                .map(s -> String.format("'%s'", s))
                .collect(Collectors.joining(", "));
            return String.format("Use one of the following dynamic version specifications: %s.", validStrings);
        }
    }

    private enum DynamicVersion {
        LATEST("latest", "current"),
        RELEASE_CANDIDATE("release-candidate", "release-candidate"),
        RELEASE_MILESTONE("release-milestone", "milestone"),
        RELEASE_NIGHTLY("release-nightly", "release-nightly"),
        NIGHTLY("nightly", "nightly");

        private final String name;

        private final String urlSuffix;

        DynamicVersion(String name, String urlSuffix) {
            this.name = name;
            this.urlSuffix = urlSuffix;
        }

        @Nullable
        public static DynamicVersion findMatch(String version) {
            return Arrays.stream(values()).filter(dv -> dv.name.equals(version)).findFirst().orElse(null);
        }
    }

    @NullMarked
    private enum RequestType {
        DYNAMIC_VERSION,
        SEMANTIC_VERSION,
        VERSION
    }

    @NullMarked
    private static class GradleVersionRequest {
        final String request;
        final RequestType requestType;
        @Nullable
        DynamicVersion dynamicVersion = null;
        @Nullable
        Integer majorVersion;
        @Nullable
        Integer minorVersion;
        private static final Pattern SEMVER_REQUEST = Pattern.compile("([0-9]+)(\\.([0-9]+))?");

        GradleVersionRequest(String request) {
            this.request = request;
            DynamicVersion dynamicVersion = DynamicVersion.findMatch(request);
            if (dynamicVersion != null) {
                this.requestType = RequestType.DYNAMIC_VERSION;
                this.dynamicVersion = dynamicVersion;
            } else {
                Matcher matcher = SEMVER_REQUEST.matcher(request);
                if (matcher.matches()) {
                    majorVersion = Integer.parseInt(matcher.group(1));
                    minorVersion = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : null;
                    if (majorVersion >= 9) {
                        this.requestType = RequestType.SEMANTIC_VERSION;
                    } else {
                        this.requestType = RequestType.VERSION;
                    }
                } else {
                    this.requestType = RequestType.VERSION;
                }
            }
        }

        GradleVersionRequest(GradleVersion gradleVersion) {
            this.request = gradleVersion.getVersion();
            this.requestType = RequestType.VERSION;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GradleVersionRequest)) {
                return false;
            }
            return Objects.equals(request, ((GradleVersionRequest) other).request);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(request);
        }
    }
}
