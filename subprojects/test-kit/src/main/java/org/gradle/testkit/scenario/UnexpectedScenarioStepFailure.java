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
import org.gradle.testkit.runner.UnexpectedBuildFailure;


/**
 * Thrown when running a scenario step that was expected to succeed, but failed.
 *
 * @see GradleScenario#run()
 * @since 6.5
 */
@Incubating
public class UnexpectedScenarioStepFailure extends UnexpectedScenarioStepException {

    public UnexpectedScenarioStepFailure(String step, String message, UnexpectedBuildFailure cause) {
        super(step, message, cause);
    }

    @Override
    public UnexpectedBuildFailure getCause() {
        return (UnexpectedBuildFailure) super.getCause();
    }
}
