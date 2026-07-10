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
package org.gradle.api.plugins.internal;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.project.ProjectConfigureAction;

//This one should go away once we complete the auto-apply plugins
public class HelpTasksAutoApplyAction implements ProjectConfigureAction {
    @Override
    public void execute(ProjectInternal project) {
        project.getPluginManager().apply("org.gradle.help-tasks");
    }
}
