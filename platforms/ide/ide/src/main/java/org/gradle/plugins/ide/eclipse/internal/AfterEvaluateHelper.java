/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.internal;

import org.gradle.api.Action;
import org.gradle.api.Project;

/**
 * Helper class for actions registered for {@code afterEvaluate}.
 *
 * See https://issues.gradle.org/browse/GRADLE-3231
 */
public class AfterEvaluateHelper {

    /**
     * Registers the target action in {@code project.afterEvaluate} or calls it directly if the target project has been already evaluated.
     *
     * @param project The target project.
     * @param action The target action.
     */
    public static void afterEvaluateOrExecute(Project project, Action<Project> action) {
        if (project.getState().getExecuted()) {
            action.execute(project);
        } else {
            project.afterEvaluate(action);
        }
    }

}
