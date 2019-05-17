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
import org.gradle.util.Path;

import java.util.Comparator;
import java.util.Set;

public class DefaultGroupTaskReportModel implements TaskReportModel {
    public static final String OTHER_GROUP = "other";
    private static final Comparator<String> STRING_COMPARATOR = GUtil.caseInsensitive();
    private SetMultimap<String, TaskDetails> groups;

    public void build(TaskReportModel model) {
        Comparator<String> keyComparator = GUtil.last(GUtil.last(STRING_COMPARATOR, OTHER_GROUP), DEFAULT_GROUP);
        Comparator<TaskDetails> taskComparator = new Comparator<TaskDetails>() {
            @Override
            public int compare(TaskDetails task1, TaskDetails task2) {
                int diff = STRING_COMPARATOR.compare(task1.getPath().getName(), task2.getPath().getName());
                if (diff != 0) {
                    return diff;
                }
                Path parent1 = task1.getPath().getParent();
                Path parent2 = task2.getPath().getParent();
                if (parent1 == null && parent2 != null) {
                    return -1;
                }
                if (parent1 != null && parent2 == null) {
                    return 1;
                }
                if (parent1 == null) {
                    return 0;
                }
                return parent1.compareTo(parent2);
            }
        };
        groups = TreeMultimap.create(keyComparator, taskComparator);
        for (String group : model.getGroups()) {
            groups.putAll(group, model.getTasksForGroup(group));
        }
        String otherGroupName = findOtherGroup(groups.keySet());
        if (otherGroupName != null && groups.keySet().contains(DEFAULT_GROUP)) {
            groups.putAll(otherGroupName, groups.removeAll(DEFAULT_GROUP));
        }
        if (groups.keySet().contains(DEFAULT_GROUP) && groups.keySet().size() > 1) {
            groups.putAll(OTHER_GROUP, groups.removeAll(DEFAULT_GROUP));
        }
    }

    private String findOtherGroup(Set<String> groupNames) {
        for (String groupName : groupNames) {
            if (groupName.equalsIgnoreCase(OTHER_GROUP)) {
                return groupName;
            }
        }
        return null;
    }

    @Override
    public Set<String> getGroups() {
        return groups.keySet();
    }

    @Override
    public Set<TaskDetails> getTasksForGroup(String group) {
        return groups.get(group);
    }
}
