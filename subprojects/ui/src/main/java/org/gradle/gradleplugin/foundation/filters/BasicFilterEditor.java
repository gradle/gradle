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
import org.gradle.foundation.common.ObserverLord;
import org.gradle.gradleplugin.foundation.DOM4JSerializer;
import org.gradle.gradleplugin.foundation.ExtensionFileFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/*
 This allows editing a BasicProjectAndTaskFilter. You don't work directly
 with the BasicProjectAndTaskFilter to alter it. Go through this.
 During the editing process, you often need to fire off notifications that
 normally aren't required. This is where those notifications would come from.
 This also has some extra validation that isn't present in the filter itself.
  */

public class BasicFilterEditor implements ProjectAndTaskFilter {
    private List<String> filteredOutProjectNames = new ArrayList<String>();
    private List<String> filteredOutTaskNames = new ArrayList<String>();
    private boolean filterOutTasksWithNoDescription;
    private ObserverLord<FilterEditorObserver> observerLord = new ObserverLord<FilterEditorObserver>();

    public interface FilterEditorObserver {
        /**
         * A generic notification that the filter has changed.
         */
        public void filterChanged();
    }

    public BasicFilterEditor() {

    }

    public BasicFilterEditor(BasicProjectAndTaskFilter filter) {
        initializeFromFilter(filter);
    }

    public void initializeFromFilter(BasicProjectAndTaskFilter filter) {
        filteredOutProjectNames.clear();
        hideProjectsByName(filter.getFilteredOutProjectNames());

        filteredOutTaskNames.clear();
        hideTasksByName(filter.getFilteredOutTaskNames());

        filterOutTasksWithNoDescription = filter.filterOutTasksWithNoDescription();
        notifyChanges();
    }

    public void addFilterEditorObserver(FilterEditorObserver observer, boolean inEventQueue) {
        observerLord.addObserver(observer, inEventQueue);
    }

    public void removeFilterEditorObserver(FilterEditorObserver observer) {
        observerLord.removeObserver(observer);
    }

    public boolean filterOutTasksWithNoDescription() {
        return filterOutTasksWithNoDescription;
    }

    public void setFilterOutTasksWithNoDescription(boolean filterOutTasksWithNoDescription) {
        this.filterOutTasksWithNoDescription = filterOutTasksWithNoDescription;
    }

    public void hideProjects(ProjectView... filteredProjects) {
        hideProjects(Arrays.asList(filteredProjects));
    }

    public void hideProjects(List<ProjectView> filteredProjects) {
        Iterator<ProjectView> iterator = filteredProjects.iterator();
        while (iterator.hasNext()) {
            ProjectView projectView = iterator.next();
            if (!filteredOutProjectNames.contains(projectView.getName())) {
                filteredOutProjectNames.add(projectView.getName());
            }
        }

        notifyChanges();
    }

    public void hideProjectsByName(String... projectNames) {
        hideProjectsByName(Arrays.asList(projectNames));
    }

    public void hideProjectsByName(List<String> projectNames) {
        Iterator<String> iterator = projectNames.iterator();
        while (iterator.hasNext()) {
            String gradleProject = iterator.next();
            if (!filteredOutProjectNames.contains(gradleProject)) {
                filteredOutProjectNames.add(gradleProject);
            }
        }
        notifyChanges();
    }

    public void showProjects(ProjectView... filteredProjects) {
        showProjects(Arrays.asList(filteredProjects));
    }

    public void showProjects(List<ProjectView> filteredProjects) {
        Iterator<ProjectView> iterator = filteredProjects.iterator();
        while (iterator.hasNext()) {
            ProjectView projectView = iterator.next();
            filteredOutProjectNames.remove(projectView.getName());
        }
        notifyChanges();
    }

    public void showProjectsByName(String... filteredProjects) {
        showProjectsByName(Arrays.asList(filteredProjects));
    }

    public void showProjectsByName(List<String> filteredProjects) {
        Iterator<String> iterator = filteredProjects.iterator();
        while (iterator.hasNext()) {
            String gradleProjectName = iterator.next();
            filteredOutProjectNames.remove(gradleProjectName);
        }
        notifyChanges();
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

    public boolean doesAllowProject(String projectName) {
        return !filteredOutProjectNames.contains(projectName);
    }

    public void hideTasks(TaskView... filteredTasks) {
        hideTasks(Arrays.asList(filteredTasks));
    }

    public void hideTasks(List<TaskView> filteredTasks) {
        Iterator<TaskView> iterator = filteredTasks.iterator();
        while (iterator.hasNext()) {
            TaskView taskView = iterator.next();
            if (!filteredOutTaskNames.contains(taskView.getName())) {
                filteredOutTaskNames.add(taskView.getName());
            }
        }
        notifyChanges();
    }

    public void hideTasksByName(String... taskNames) {
        hideTasksByName(Arrays.asList(taskNames));
    }

    public void hideTasksByName(List<String> taskNames) {
        Iterator<String> iterator = taskNames.iterator();
        while (iterator.hasNext()) {
            String gradleTask = iterator.next();
            if (!filteredOutTaskNames.contains(gradleTask)) {
                filteredOutTaskNames.add(gradleTask);
            }
        }
        notifyChanges();
    }

    public void showTasks(TaskView... filteredTasks) {
        showTasks(Arrays.asList(filteredTasks));
    }

    public void showTasks(List<TaskView> filteredTasks) {
        Iterator<TaskView> iterator = filteredTasks.iterator();
        while (iterator.hasNext()) {
            TaskView taskView = iterator.next();
            filteredOutTaskNames.remove(taskView.getName());
        }
        notifyChanges();
    }

    public void showTasksByName(String... filteredTasks) {
        showTasksByName(Arrays.asList(filteredTasks));
    }

    public void showTasksByName(List<String> filteredTasks) {
        Iterator<String> iterator = filteredTasks.iterator();
        while (iterator.hasNext()) {
            String gradleTaskName = iterator.next();
            filteredOutTaskNames.remove(gradleTaskName);
        }
        notifyChanges();
    }

    /**
     * Determines if the specified task should be allowed or not.
     *
     * @param task the task in question
     * @return true to allow it, false not to.
     */
    public boolean doesAllowTask(TaskView task) {
        //since we've got the task here, we can more than just filter it by name. We can
        //filter it out if it has no description.
        return BasicProjectAndTaskFilter.doesAllowTask(task, filteredOutTaskNames, filterOutTasksWithNoDescription);
    }

    public boolean doesAllowTask(String taskName) {
        return !filteredOutTaskNames.contains(taskName);
    }

    public BasicProjectAndTaskFilter createFilter() {
        return new BasicProjectAndTaskFilter(filteredOutProjectNames, filteredOutTaskNames, filterOutTasksWithNoDescription);
    }

    /**
     * Call this to save this filter to a file.
     */
    public void exportToFile(DOM4JSerializer.ExportInteraction exportInteraction) {
        BasicProjectAndTaskFilter basicProjectAndTaskFilter = createFilter();
        DOM4JSerializer.exportToFile("basic-filter", exportInteraction, createFileFilter(), basicProjectAndTaskFilter);
    }

    /**
     * Call this to read a filter from a file.
     */
    public boolean importFromFile(DOM4JSerializer.ImportInteraction importInteraction) {
        BasicProjectAndTaskFilter basicProjectAndTaskFilter = new BasicProjectAndTaskFilter();
        if (!DOM4JSerializer.importFromFile(importInteraction, createFileFilter(), basicProjectAndTaskFilter)) {
            return false;
        }

        initializeFromFilter(basicProjectAndTaskFilter);
        notifyChanges();
        return true;
    }

    /**
     * This creates a file filter suitable for storing/reading this filter.
     */
    private ExtensionFileFilter createFileFilter() {
        return new ExtensionFileFilter(".task-filter", "Task Filter");
    }

    /**
     * Call this whenever you make changes so we can notify any observers.
     */
    private void notifyChanges() {
        observerLord.notifyObservers(new ObserverLord.ObserverNotification<FilterEditorObserver>() {
            public void notify(FilterEditorObserver observer) {
                observer.filterChanged();
            }
        });
    }
}
