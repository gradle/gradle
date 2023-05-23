/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblem;
import org.gradle.tooling.internal.protocol.events.InternalFailureResult;

import java.util.List;

public class DefaultProjectConfigurationFailureResult extends AbstractProjectConfigurationResult implements InternalFailureResult {

    private final List<InternalFailure> failures;
    private final List<InternalProblem> problems;

    public DefaultProjectConfigurationFailureResult(long startTime, long endTime, List<InternalFailure> failures, List<? extends InternalPluginApplicationResult> pluginApplicationResults, List<InternalProblem> problems) {
        super(startTime, endTime, "failed", pluginApplicationResults, problems);
        this.failures = failures;
        this.problems = problems;
    }

    @Override
    public List<InternalFailure> getFailures() {
        return failures;
    }


    @Override
    public List<InternalProblem> getProblems() {
        return problems;
    }
}
