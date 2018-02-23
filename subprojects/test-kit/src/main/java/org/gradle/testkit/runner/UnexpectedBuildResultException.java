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

/**
 * Base class for {@link UnexpectedBuildFailure} and {@link UnexpectedBuildSuccess}.
 *
 * @since 2.9
 * @see BuildResult
 */
public abstract class UnexpectedBuildResultException extends RuntimeException {

    private final BuildResult buildResult;

    UnexpectedBuildResultException(String message, BuildResult buildResult) {
        super(message);
        this.buildResult = buildResult;
    }

    /**
     * The result of the build execution.
     *
     * @return the result of the build execution
     */
    public BuildResult getBuildResult() {
        return buildResult;
    }
}
