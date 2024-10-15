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

package org.gradle.tooling.internal.consumer;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.Failure;
import org.gradle.tooling.ProblemAwareFailure;
import org.gradle.tooling.events.problems.ProblemReport;

import java.util.List;

@NonNullApi
public final class DefaultProblemAwareFailure extends DefaultFailure implements ProblemAwareFailure {

    private final List<ProblemReport> problems;

    public DefaultProblemAwareFailure(String message, String description, List<? extends Failure> causes, List<ProblemReport> problems) {
        super(message, description, causes);
        this.problems = problems;
    }

    @Override
    public List<ProblemReport> getProblems() {
        return problems;
    }
}
