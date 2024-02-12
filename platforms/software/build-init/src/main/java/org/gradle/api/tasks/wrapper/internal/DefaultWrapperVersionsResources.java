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

package org.gradle.api.tasks.wrapper.internal;

import org.gradle.api.GradleException;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.wrapper.WrapperVersionsResources;
import org.gradle.internal.exceptions.ResolutionProvider;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultWrapperVersionsResources implements WrapperVersionsResources {
    public static final String LATEST = "latest";
    public static final String NIGHTLY = "nightly";
    public static final String RELEASE_NIGHTLY = "release-nightly";
    public static final String RELEASE_CANDIDATE = "release-candidate";
    public static final List<String> PLACE_HOLDERS = Arrays.asList(LATEST, RELEASE_CANDIDATE, RELEASE_NIGHTLY, NIGHTLY); // order these from most to least stable
    private final TextResource latest;
    private final TextResource releaseCandidate;
    private final TextResource nightly;
    private final TextResource releaseNightly;

    public DefaultWrapperVersionsResources(TextResource latest, TextResource releaseCandidate, TextResource nightly, TextResource releaseNightly) {

        this.latest = latest;
        this.releaseCandidate = releaseCandidate;
        this.nightly = nightly;
        this.releaseNightly = releaseNightly;
    }

    public TextResource getLatest() {
        return latest;
    }

    public TextResource getReleaseCandidate() {
        return releaseCandidate;
    }

    public TextResource getNightly() {
        return nightly;
    }

    public TextResource getReleaseNightly() {
        return releaseNightly;
    }

    /**
     * This exception is thrown when the wrapper task is run in an attempt to update the wrapper version and
     * an invalid version is specified.
     */
    public static final class WrapperVersionException extends GradleException implements ResolutionProvider {
        public WrapperVersionException(String message) {
            super(message);
        }

        public WrapperVersionException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }

        @Override
        public List<String> getResolutions() {
            return Arrays.asList(suggestActualVersion(), suggestDynamicVersions());
        }

        private String suggestActualVersion() {
            return "Specify a valid Gradle release listed on https://gradle.org/releases/.";
        }

        private String suggestDynamicVersions() {
            String validStrings = DefaultWrapperVersionsResources.PLACE_HOLDERS.stream()
                .map(s -> String.format("'%s'", s))
                .collect(Collectors.joining(", "));
            return String.format("Use one of the following dynamic version specifications: %s.", validStrings);
        }
    }
}
