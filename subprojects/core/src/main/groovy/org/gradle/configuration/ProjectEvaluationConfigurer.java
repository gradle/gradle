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
import org.gradle.api.internal.project.ProjectInternal;

public class ProjectEvaluationConfigurer implements Action<ProjectInternal> {

    public void execute(ProjectInternal projectInternal) {
        //TODO SF this logic belongs elsewhere, better support of the system property
        String prop = projectInternal.getGradle().getStartParameter().getSystemPropertiesArgs().get("org.gradle.configuration.ondemand");
        if ("true".equals(prop)) {
            //if experimental configuration on demand is 'on', we'll only evaluate the root project.
            //remaining projects are evaluated on demand
            if (projectInternal.getPath().equals(":")) {
                projectInternal.evaluate();
            }
        } else {
            //the default behavior, evaluate the project as requested
            projectInternal.evaluate();
        }
    }
}
