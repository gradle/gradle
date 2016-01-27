/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.execution;

import com.google.common.collect.Lists;
import org.gradle.api.internal.GradleInternal;

import java.util.List;

public class DefaultBuildExecuter implements BuildExecuter {
    private final List<BuildExecutionAction> executionActions;

    public DefaultBuildExecuter(Iterable<? extends BuildExecutionAction> executionActions) {
        this.executionActions = Lists.newArrayList(executionActions);
    }
    public void execute(GradleInternal gradle) {
        execute(gradle, 0);
    }

    private void execute(final GradleInternal gradle, final int index) {
        if (index >= executionActions.size()) {
            return;
        }
        executionActions.get(index).execute(new BuildExecutionContext() {
            public GradleInternal getGradle() {
                return gradle;
            }

            public void proceed() {
                execute(gradle, index + 1);
            }

        });
    }
}
