/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner;

import org.gradle.api.Incubating;

/**
 * Thrown when executing a build that failed unexpectedly.
 * Provides a build result for further inspection or making assertions about the build outcome (e.g. standard output).
 *
 * @since 2.9
 * @see BuildResult
 */
@Incubating
public class UnexpectedBuildException extends RuntimeException {
    private final BuildResult buildResult;

    public UnexpectedBuildException(String message, BuildResult buildResult) {
        super(message);
        this.buildResult = buildResult;
    }

    public UnexpectedBuildException(Throwable t, BuildResult buildResult) {
        super(t);
        this.buildResult = buildResult;
    }

    public BuildResult getBuildResult() {
        return buildResult;
    }
}
