/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal.action;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.project.ProjectConfigureAction;

public class CustomInitAutoApplyAction implements ProjectConfigureAction {

    @Override
    public void execute(final ProjectInternal project) {
        System.out.println("Running custom init auto apply action");
        project.addDeferredConfiguration(() -> {
            StartParameterInternal startParameter = project.getGradle().getStartParameter();
            System.out.println("In auto-apply action: " + startParameter);
            String customPluginId = startParameter.getCustomInitPluginId();
            if (customPluginId != null) {
                //project.getPlugins().apply(customPluginId);
            }
        });
        project.getPluginManager().apply("org.gradle.custom-init");
    }

}
