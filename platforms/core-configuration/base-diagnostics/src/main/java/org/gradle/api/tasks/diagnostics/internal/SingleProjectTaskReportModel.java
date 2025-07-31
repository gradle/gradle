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
package org.gradle.api.tasks.diagnostics.internal;

import com.google.common.collect.ImmutableSetMultimap;

import java.util.Set;

public class SingleProjectTaskReportModel implements TaskReportModel {

    private final ImmutableSetMultimap<String, TaskDetails> groups;

    public SingleProjectTaskReportModel(ImmutableSetMultimap<String, TaskDetails> groups) {
        this.groups = groups;
    }

    @Override
    public Set<String> getGroups() {
        return groups.keySet();
    }

    @Override
    public Set<TaskDetails> getTasksForGroup(String group) {
        if (!groups.containsKey(group)) {
            throw new IllegalArgumentException(String.format("Unknown group '%s'", group));
        }
        return groups.get(group);
    }

}
