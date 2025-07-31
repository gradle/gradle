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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.composite.BuildIncludeListener;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class BrokenBuildsCapturingListener implements BuildIncludeListener {

    private final FailureFactory failureFactory;
    private final Map<BuildState, Failure> brokenBuilds = new HashMap<>();
    private final Map<SettingsInternal, Failure> brokenSettings = new HashMap<>();

    public BrokenBuildsCapturingListener(
        FailureFactory failureFactory
    ) {
        this.failureFactory = failureFactory;
    }

    @Override
    public void buildInclusionFailed(BuildState buildState, @Nullable Exception exception) {
        Failure failure = failureFactory.create(exception);
        brokenBuilds.put(buildState, failure);
    }

    @Override
    public Map<BuildState, Failure> getBrokenBuilds() {
        return brokenBuilds;
    }

    @Override
    public void settingsScriptFailed(SettingsInternal settingsScript, LocationAwareException e) {
        getBrokenSettings().put(settingsScript, failureFactory.create(e));
    }

    @Override
    public Map<SettingsInternal, Failure> getBrokenSettings() {
        return brokenSettings;
    }
}
