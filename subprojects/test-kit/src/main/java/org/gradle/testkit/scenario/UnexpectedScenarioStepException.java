/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testkit.scenario;

import org.gradle.api.Incubating;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.UnexpectedBuildResultException;


/**
 * Base class for {@link UnexpectedScenarioStepFailure} and {@link UnexpectedScenarioStepSuccess}.
 *
 * @see ScenarioResult
 * @since 6.5
 */
@Incubating
public class UnexpectedScenarioStepException extends RuntimeException {

    private final String step;

    UnexpectedScenarioStepException(String step, String message, UnexpectedBuildResultException cause) {
        super(message, cause);
        this.step = step;
    }

    public String getStep() {
        return step;
    }

    @Override
    public UnexpectedBuildResultException getCause() {
        return (UnexpectedBuildResultException) super.getCause();
    }

    public BuildResult getBuildResult() {
        return getCause().getBuildResult();
    }
}
