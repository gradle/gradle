/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.configuration.project;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.service.ServiceLocator;

public class PluginsProjectConfigureActions implements ProjectConfigureAction {

    public static ProjectConfigureAction from(ServiceLocator serviceLocator) {
        return of(ProjectConfigureAction.class, serviceLocator);
    }

    public static <T extends Action<ProjectInternal>> ProjectConfigureAction of(Class<T> serviceType,
                                                                                ServiceLocator serviceLocator) {
        return new PluginsProjectConfigureActions(
            ImmutableList.<Action<ProjectInternal>>copyOf(
                serviceLocator.getAll(serviceType)));
    }

    private final Iterable<Action<ProjectInternal>> actions;

    private PluginsProjectConfigureActions(Iterable<Action<ProjectInternal>> actions) {
        this.actions = actions;
    }

    public void execute(ProjectInternal project) {
        for (Action<ProjectInternal> action : actions) {
            action.execute(project);
        }
    }
}
