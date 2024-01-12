/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.fixture;

import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.problems.BaseProblemDescriptor;
import org.gradle.tooling.events.problems.ProblemEvent;

import java.util.ArrayList;
import java.util.List;

public class ProblemEventListener implements ProgressListener {
    List<BaseProblemDescriptor> allProblems = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @Override
    public void statusChanged(ProgressEvent event) {
        if (event instanceof ProblemEvent) {
            ProblemEvent problemEvent = (ProblemEvent) event;

            this.allProblems.add(problemEvent.getDescriptor());
        }
    }

    public List<BaseProblemDescriptor> getProblems() {
        return allProblems;
    }
}
