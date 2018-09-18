/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;

import javax.annotation.Nullable;

/**
 * <p>A {@code BuildResult} packages up the results of a build executed by a {@link org.gradle.initialization.GradleLauncher} instance.</p>
 */
public class BuildResult {
    private final String action;
    private final Throwable failure;
    private final Gradle gradle;

    public BuildResult(@Nullable Gradle gradle, @Nullable Throwable failure) {
        this("Build", gradle, failure);
    }

    public BuildResult(String action, @Nullable Gradle gradle, @Nullable Throwable failure) {
        this.action = action;
        this.gradle = gradle;
        this.failure = failure;
    }

    @Nullable
    public Gradle getGradle() {
        return gradle;
    }

    @Nullable
    public Throwable getFailure() {
        return failure;
    }

    /**
     * The action performed by this build. Either `Build` or `Configure`.
     */
    public String getAction() {
        return action;
    }

    /**
     * <p>Rethrows the build failure. Does nothing if there was no build failure.</p>
     */
    public BuildResult rethrowFailure() {
        if (failure instanceof GradleException) {
            throw (GradleException) failure;
        }
        if (failure != null) {
            throw new GradleException(action + " aborted because of an internal error.", failure);
        }
        return this;
    }
}
