/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultBuildConfigurer implements BuildConfigurer {
    private List<Action<? super ProjectInternal>> actions;

    public DefaultBuildConfigurer(Action<? super ProjectInternal>... actions) {
        this.actions = new ArrayList<Action<? super ProjectInternal>>(Arrays.asList(actions));
    }

    public void configure(GradleInternal gradle) {
        gradle.getRootProject().allprojects(new Action<Project>() {
            public void execute(Project project) {
                for (Action<? super ProjectInternal> action : actions) {
                    action.execute((ProjectInternal) project);
                }
            }
        });
    }
}
