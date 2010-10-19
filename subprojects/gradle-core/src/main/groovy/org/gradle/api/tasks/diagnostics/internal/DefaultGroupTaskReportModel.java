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

import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;
import org.gradle.util.GUtil;

import java.util.Comparator;
import java.util.Set;

public class DefaultGroupTaskReportModel implements TaskReportModel {
    private SetMultimap<String, TaskDetails> groups;

    public void build(TaskReportModel model) {
        Comparator<String> keyComparator = GUtil.last(GUtil.last(GUtil.caseInsensitive(), "other"), "");
        Comparator<TaskDetails> taskComparator = new Comparator<TaskDetails>() {
            public int compare(TaskDetails task1, TaskDetails task2) {
                return GUtil.caseInsensitive().compare(task1.getPath(), task2.getPath());
            }
        };
        groups = TreeMultimap.create(keyComparator, taskComparator);
        for (String group : model.getGroups()) {
            groups.putAll(group, model.getTasksForGroup(group));
        }
        String otherGroupName = findOtherGroup(groups.keySet());
        if (otherGroupName != null && groups.keySet().contains("")) {
            groups.putAll(otherGroupName, groups.removeAll(""));
        }
        if (groups.keySet().contains("") && groups.keySet().size() > 1) {
            groups.putAll("other", groups.removeAll(""));
        }
    }

    private String findOtherGroup(Set<String> groupNames) {
        for (String groupName : groupNames) {
            if (groupName.equalsIgnoreCase("other")) {
                return groupName;
            }
        }
        return null;
    }

    public Set<String> getGroups() {
        return groups.keySet();
    }

    public Set<TaskDetails> getTasksForGroup(String group) {
        return groups.get(group);
    }
}
