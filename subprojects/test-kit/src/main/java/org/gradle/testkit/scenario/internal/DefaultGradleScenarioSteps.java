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

package org.gradle.testkit.scenario.internal;

import org.gradle.testkit.scenario.GradleScenarioStep;
import org.gradle.testkit.scenario.GradleScenarioSteps;

import java.util.ArrayList;
import java.util.List;


public class DefaultGradleScenarioSteps implements GradleScenarioSteps {

    private final List<DefaultGradleScenarioStep> steps = new ArrayList<>();

    @Override
    public GradleScenarioStep named(String name) {
        DefaultGradleScenarioStep step = new DefaultGradleScenarioStep(name);
        steps.add(step);
        return step;
    }

    List<DefaultGradleScenarioStep> getSteps() {
        return steps;
    }
}
