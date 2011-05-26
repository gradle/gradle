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

package org.gradle.tooling.internal.provider;

import org.apache.commons.lang.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author: Szczepan Faber, created at: 5/26/11
 */
public class EclipsePluginApplierResult {

    private Set<String> allAppliedTasks = new HashSet<String>();

    public boolean wasApplied(String taskPath) {
        return allAppliedTasks.contains(taskPath);
    }

    public void rememberTasks(String projectPath, Collection<String> taskNames) {
        for (String taskName : taskNames) {
            String taskPath = StringUtils.removeEnd(projectPath, ":") + ":" + taskName;
            allAppliedTasks.add(taskPath);
        }
    }
}
