/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.wrapper;

import org.gradle.api.tasks.wrapper.internal.DefaultWrapperVersionsAPI;
import org.gradle.api.tasks.wrapper.internal.DefaultWrapperVersionsAPI.WrapperVersionException;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// TODO: refactor this class after migration to provider API
@NullMarked
class GradleVersionResolver {
    @Nullable
    private GradleVersion gradleVersion;
    private GradleVersionRequest gradleVersionRequest = new GradleVersionRequest(GradleVersion.current());
    @Nullable
    private DefaultWrapperVersionsAPI wrapperVersionsResources;

    void setWrapperVersionsResources(DefaultWrapperVersionsAPI wrapperVersionsResources) {
        this.wrapperVersionsResources = wrapperVersionsResources;
    }

    private GradleVersion resolve() {
        switch (gradleVersionRequest.requestType) {
            case PLACEHOLDER:
                String version = wrapperVersionsResources.getSingleVersion(gradleVersionRequest.request);
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
        Stream<GradleVersion> versions = wrapperVersionsResources.getVersionsList(majorVersion.toString())
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
                new WrapperVersionException("Invalid version specified for argument '--gradle-version': no final version found for major version " + majorVersion)
            );
        } else {
            return versions
                .filter(v -> getMinorVersion(v) == minorVersion)
                .max(GradleVersion::compareTo).orElseThrow(() ->
                    new WrapperVersionException("Invalid version specified for argument '--gradle-version': no final version found for version " + majorVersion + "." + minorVersion)
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

    GradleVersion getGradleVersion() {
        if (gradleVersion == null) {
            gradleVersion = resolve();
        }
        return gradleVersion;
    }

    void setGradleVersionRequest(String request) {
        GradleVersionRequest gradleVersionRequest = new GradleVersionRequest(request);
        if (gradleVersionRequest.requestType == RequestType.VERSION) {
            try {
                this.gradleVersion = GradleVersion.version(request);
            } catch (Exception e) {
                throw new WrapperVersionException("Invalid version specified for argument '--gradle-version'", e);
            }
        } else if (!Objects.equals(this.gradleVersionRequest, gradleVersionRequest)) {
            this.gradleVersionRequest = gradleVersionRequest;
            this.gradleVersion = null;
        }
    }

    @NullMarked
    private enum RequestType {
        PLACEHOLDER,
        SEMANTIC_VERSION,
        VERSION
    }

    @NullMarked
    private static class GradleVersionRequest {
        final String request;
        final RequestType requestType;
        @Nullable
        Integer majorVersion;
        @Nullable
        Integer minorVersion;
        private static final Pattern SEMVER_REQUEST = Pattern.compile("([0-9]+)(\\.([0-9]+))?");

        GradleVersionRequest(String request) {
            this.request = request;
            if (DefaultWrapperVersionsAPI.isPlaceHolder(request)) {
                this.requestType = RequestType.PLACEHOLDER;
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
