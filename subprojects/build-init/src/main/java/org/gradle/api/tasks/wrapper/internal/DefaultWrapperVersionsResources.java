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

import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.wrapper.WrapperVersionsResources;

import java.util.Arrays;
import java.util.List;

public class DefaultWrapperVersionsResources implements WrapperVersionsResources {
    public static final String LATEST = "latest";
    public static final String NIGHTLY = "nightly";
    public static final String RELEASE_NIGHTLY = "release-nightly";
    public static final String RELEASE_CANDIDATE = "release-candidate";
    public static final List<String> PLACE_HOLDERS = Arrays.asList(LATEST, NIGHTLY, RELEASE_NIGHTLY, RELEASE_CANDIDATE);
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

}
