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
import org.gradle.api.internal.Actions;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class DefaultBuildConfigurer implements BuildConfigurer {
    private Action<Project> actions;
    private final static Logger LOG = Logging.getLogger(DefaultBuildConfigurer.class);

    public DefaultBuildConfigurer(Action<? super ProjectInternal>... actions) {
        this.actions = Actions.castBefore(ProjectInternal.class, Actions.composite(actions));
    }

    public void configure(GradleInternal gradle) {
        if (gradle.getStartParameter().isConfigureOnDemand()) {
            LOG.lifecycle("The configuration-on-demand mode is incubating. Enjoy it and let us know how it works for you.");
            gradle.addProjectEvaluationListener(new ImplicitTasksConfigurer());
            gradle.getRootProject().evaluate();
        } else {
            gradle.getRootProject().allprojects(actions);
        }
    }
}