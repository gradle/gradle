/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r112;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.TaskSelector;

public class FetchTaskSelectorsBuildAction implements BuildAction<Map<String, Set<String>>> {
    public Map<String, DomainObjectSet<? extends TaskSelector>> execute(BuildController controller) {
        // Use a GradleProject to reference a project
        GradleProject rootProject = controller.getModel(GradleProject.class);
        Map<String, DomainObjectSet<? extends TaskSelector>> results = new HashMap<String, Set<String>>();
        visit(rootProject, results);
        return results;
    }

    void visit(GradleProject project, Map<String, Set<String>> results) {
        Set<String> tsNames = new HashSet<String>();
        for (TaskSelector ts: project.taskSelectors) {
            tsNames.add(ts.name);
        }
        results.put(project.name, tsNames);
        for (GradleProject child : project.getChildren()) {
            visit(child, results);
        }
    }
}
