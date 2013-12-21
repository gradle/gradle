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
package org.gradle.gradleplugin.foundation.filters;

import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.foundation.settings.SettingsSerializable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This is a basic filter where you can specify specific projects and tasks that will be filtered out.
 */
public class BasicProjectAndTaskFilter implements ProjectAndTaskFilter, SettingsSerializable {
    private static final String BASIC_PROJECT_AND_TASK_FILTER = "basic-project-and-task-filter";
    private static final String FILTERED_OUT_PROJECTS = "filtered-out-projects";
    private static final String FILTERED_OUT_TASKS = "filtered-out-tasks";
    private static final String FILTER_OUT_TASKS_WITH_NO_DESCRIPTION = "filter-out-tasks-with-no-description";
    private static final String ITEM = "item";
    private static final String VALUE = "value";

    private List<String> filteredOutProjectNames = new ArrayList<String>();
    private List<String> filteredOutTaskNames = new ArrayList<String>();
    private boolean filterOutTasksWithNoDescription;

    public BasicProjectAndTaskFilter(List<String> filteredOutProjectNames, List<String> filteredOutTaskNames, boolean filterOutTasksWithNoDescription) {
        this.filterOutTasksWithNoDescription = filterOutTasksWithNoDescription;
        this.filteredOutProjectNames.addAll(filteredOutProjectNames);
        this.filteredOutTaskNames.addAll(filteredOutTaskNames);
    }

    public BasicProjectAndTaskFilter() {
    }

    public List<String> getFilteredOutProjectNames() {
        return filteredOutProjectNames;
    }

    /*package*/

    void setFilteredOutProjectNames(List<String> filteredOutProjectNames) {
        this.filteredOutProjectNames = filteredOutProjectNames;
    }

    public List<String> getFilteredOutTaskNames() {
        return filteredOutTaskNames;
    }

    /*package*/

    void setFilteredOutTaskNames(List<String> filteredOutTaskNames) {
        this.filteredOutTaskNames = filteredOutTaskNames;
    }

    public boolean filterOutTasksWithNoDescription() {
        return filterOutTasksWithNoDescription;
    }

    /*package*/

    void setFilterOutTasksWithNoDescription(boolean filterOutTasksWithNoDescription) {
        this.filterOutTasksWithNoDescription = filterOutTasksWithNoDescription;
    }

    /**
     * Determines if the specified project should be allowed or not.
     *
     * @param project the project in question
     * @return true to allow it, false not to.
     */
    public boolean doesAllowProject(ProjectView project) {
        return !filteredOutProjectNames.contains(project.getName());
    }

    /**
     * Determines if the specified task should be allowed or not.
     *
     * @param task the task in question
     * @return true to allow it, false not to.
     */
    public boolean doesAllowTask(TaskView task) {
        return doesAllowTask(task, filteredOutTaskNames, filterOutTasksWithNoDescription);
    }

    /**
     * Determines if the specified task should be allowed or not.
     *
     * This version is static and is shared by this filter and its editor.
     *
     * @param task the task in question
     * @param filteredOutTasks a list of filtered out task names
     * @param filterOutTasksWithNoDescription whether or not to hide it if it as no description
     * @return true if the task is allowed, false if not.
     */
    public static boolean doesAllowTask(TaskView task, List<String> filteredOutTasks, boolean filterOutTasksWithNoDescription) {
        if (filterOutTasksWithNoDescription) {
            if (!task.hasDescription()) {
                return false;
            }
        }

        return !filteredOutTasks.contains(task.getName());
    }

    /**
     * Call this to saves the current settings.
     *
     * @param settings where you save the settings.
     */
    public void serializeOut(SettingsNode settings) {
        SettingsNode rootNode = settings.addChildIfNotPresent(BASIC_PROJECT_AND_TASK_FILTER);
        rootNode.removeAllChildren(); //clear out whatever may have already been there

        rootNode.setValueOfChildAsBoolean(FILTER_OUT_TASKS_WITH_NO_DESCRIPTION, filterOutTasksWithNoDescription);

        SettingsNode filteredOutProjectsNode = rootNode.addChild(FILTERED_OUT_PROJECTS);
        serializeOutStringList(filteredOutProjectsNode, filteredOutProjectNames);

        SettingsNode filteredOutTasksNode = rootNode.addChild(FILTERED_OUT_TASKS);
        serializeOutStringList(filteredOutTasksNode, filteredOutTaskNames);
    }

    /**
     * Writes out a list of strings as 'item' element children of parentElement.
     */
    private void serializeOutStringList(SettingsNode parentNode, List<String> strings) {
        Iterator<String> iterator = strings.iterator();
        while (iterator.hasNext()) {
            String item = iterator.next();
            SettingsNode itemNode = parentNode.addChild(ITEM);
            itemNode.setValue(item);
        }
    }

    /**
     * Call this to read in this object's settings. The reverse of serializeOut.
     *
     * @param settings where you read your settings.
     */
    public void serializeIn(SettingsNode settings) {
        filteredOutProjectNames.clear();
        filteredOutTaskNames.clear();

        SettingsNode rootNode = settings.getChildNode(BASIC_PROJECT_AND_TASK_FILTER);
        if (rootNode == null) {
            return;
        }

        filterOutTasksWithNoDescription = rootNode.getValueOfChildAsBoolean(FILTER_OUT_TASKS_WITH_NO_DESCRIPTION, filterOutTasksWithNoDescription);

        SettingsNode filteredOutProjectsNode = rootNode.getChildNode(FILTERED_OUT_PROJECTS);
        if (filteredOutProjectsNode != null) {
            serializeInStringList(filteredOutProjectsNode, filteredOutProjectNames);
        }

        SettingsNode filteredOutTasksNode = rootNode.getChildNode(FILTERED_OUT_TASKS);
        if (filteredOutTasksNode != null) {
            serializeInStringList(filteredOutTasksNode, filteredOutTaskNames);
        }
    }

    /**
     * Reads in a list of strings as 'item' element children of parentElement.
     */
    private void serializeInStringList(SettingsNode parentNode, List<String> strings) {
        Iterator<SettingsNode> iterator = parentNode.getChildNodes(ITEM).iterator();
        while (iterator.hasNext()) {
            SettingsNode itemNode = iterator.next();
            String item = itemNode.getValue();
            if (item != null) {
                strings.add(item);
            }
        }
    }
}
