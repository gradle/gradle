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
import org.gradle.api.invocation.Build;
import org.gradle.api.initialization.Settings;

/**
 * <p>A {@code BuildResult} packages up the results of a build executed by a {@link Gradle} instance.</p>
 */
public class BuildResult {
    private final Settings settings;
    private final Throwable failure;
    private final Build build;

    public BuildResult(Settings settings, Build build, Throwable failure) {
        this.settings = settings;
        this.build = build;
        this.failure = failure;
    }

    public Settings getSettings() {
        return settings;
    }

    public Build getBuild() {
        return build;
    }

    public Throwable getFailure() {
        return failure;
    }

    /**
     * <p>Rethrows the build failure. Does nothing if there was no build failure.</p>
     */
    public void rethrowFailure() {
        if (failure instanceof GradleException) {
            throw (GradleException) failure;
        }
        if (failure != null) {
            throw new GradleException("Build aborted because of an internal error.", failure);
        }
    }
}
