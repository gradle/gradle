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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.scenario.ScenarioResult;

import java.util.LinkedHashMap;

public class DefaultScenarioResult implements ScenarioResult {

    private final LinkedHashMap<String, BuildResult> results;

    public DefaultScenarioResult(LinkedHashMap<String, BuildResult> results) {
        this.results = results;
    }

    @Override
    public BuildResult ofStep(String name) {
        BuildResult result = results.get(name);
        if (result == null) {
            throw new IllegalArgumentException("No result for scenario step named '" + name + "'");
        }
        return result;
    }
}
