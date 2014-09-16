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

package org.gradle.api.internal.project;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.lang.GroovyObject;
import groovy.lang.MissingPropertyException;
import groovy.util.ObservableMap;
import org.apache.tools.ant.MagicNames;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.PropertyHelper;
import org.apache.tools.ant.Target;
import org.gradle.api.*;
import org.gradle.api.internal.project.ant.BasicAntBuilder;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.ant.AntTarget;
import org.gradle.internal.Transformers;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;

public class DefaultAntBuilder extends BasicAntBuilder implements GroovyObject {

    private final Project gradleProject;

    public DefaultAntBuilder(Project gradleProject) {
        this.gradleProject = gradleProject;
    }

    public void propertyMissing(String property, Object newValue) {
        doSetProperty(property, newValue);
    }

    @SuppressWarnings("deprecation")
    private void doSetProperty(String property, Object newValue) {
        PropertyHelper.getPropertyHelper(getProject()).setUserProperty(null, property, newValue);
    }

    public Object propertyMissing(String name) {
        if (getProject().getProperties().containsKey(name)) {
            return getProject().getProperties().get(name);
        }

        throw new MissingPropertyException(name, getClass());
    }

    public Map<String, Object> getProperties() {
        ObservableMap map = new ObservableMap(getProject().getProperties());
        map.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                doSetProperty(event.getPropertyName(), event.getNewValue());
            }
        });

        @SuppressWarnings("unchecked") Map<String, Object> castMap = (Map<String, Object>) map;
        return castMap;
    }

    public Map<String, Object> getReferences() {
        ObservableMap map = new ObservableMap(getProject().getReferences());
        map.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                getProject().addReference(event.getPropertyName(), event.getNewValue());
            }
        });

        @SuppressWarnings("unchecked") Map<String, Object> castMap = (Map<String, Object>) map;
        return castMap;
    }

    public void importBuild(Object antBuildFile) {
        importBuild(antBuildFile, Transformers.<String>noOpTransformer());
    }

    public void importBuild(Object antBuildFile, Transformer<? extends String, ? super String> taskNamer) {
        File file = gradleProject.file(antBuildFile);
        final File baseDir = file.getParentFile();

        Set<String> existingAntTargets = new HashSet<String>(getAntProject().getTargets().keySet());
        File oldBaseDir = getAntProject().getBaseDir();
        getAntProject().setBaseDir(baseDir);
        try {
            getAntProject().setUserProperty(MagicNames.ANT_FILE, file.getAbsolutePath());
            ProjectHelper.configureProject(getAntProject(), file);
        } catch (Exception e) {
            throw new GradleException("Could not import Ant build file '" + String.valueOf(file) + "'.", e);
        } finally {
            getAntProject().setBaseDir(oldBaseDir);
        }

        // Chuck away the implicit target. It has already been executed
        getAntProject().getTargets().remove("");

        // Add an adapter for each newly added target
        Set<String> newAntTargets = new HashSet<String>(getAntProject().getTargets().keySet());
        newAntTargets.removeAll(existingAntTargets);
        for (String name : newAntTargets) {
            final Target target = getAntProject().getTargets().get(name);
            String taskName = taskNamer.transform(target.getName());
            final AntTarget task = gradleProject.getTasks().create(taskName, AntTarget.class);
            configureTask(target, task, baseDir, taskNamer);
        }
    }

    private static void configureTask(Target target, AntTarget task, File baseDir, Transformer<? extends String, ? super String> taskNamer) {
        task.setTarget(target);
        task.setBaseDir(baseDir);

        final List<String> taskDependencyNames = getTaskDependencyNames(target, taskNamer);
        task.dependsOn(new AntTargetsTaskDependency(taskDependencyNames));
        addDependencyOrdering(taskDependencyNames, task.getProject().getTasks());
    }

    private static List<String> getTaskDependencyNames(Target target, Transformer<? extends String, ? super String> taskNamer) {
        Enumeration<String> dependencies = target.getDependencies();
        List<String> taskDependencyNames = Lists.newLinkedList();
        while (dependencies.hasMoreElements()) {
            String targetName = dependencies.nextElement();
            String taskName = taskNamer.transform(targetName);
            taskDependencyNames.add(taskName);
        }
        return taskDependencyNames;
    }

    private static void addDependencyOrdering(List<String> dependencies, TaskContainer tasks) {
        String previous = null;
        for (final String dependency : dependencies) {
            if (previous != null) {
                final String finalPrevious = previous;
                tasks.all(new Action<Task>() {
                    public void execute(Task task) {
                        if (task.getName().equals(dependency)) {
                            task.shouldRunAfter(finalPrevious);
                        }
                    }
                });
            }

            previous = dependency;
        }
    }

    private static class AntTargetsTaskDependency implements TaskDependency {
        private final List<String> taskDependencyNames;

        public AntTargetsTaskDependency(List<String> taskDependencyNames) {
            this.taskDependencyNames = taskDependencyNames;
        }

        public Set<? extends Task> getDependencies(Task task) {
            Set<Task> tasks = Sets.newHashSetWithExpectedSize(taskDependencyNames.size());
            for (String dependedOnTaskName : taskDependencyNames) {
                Task dependency = task.getProject().getTasks().findByName(dependedOnTaskName);
                if (dependency == null) {
                    throw new UnknownTaskException(String.format("Imported Ant target '%s' depends on target or task '%s' which does not exist", task.getName(), dependedOnTaskName));
                }
                tasks.add(dependency);
            }
            return tasks;
        }
    }
}
