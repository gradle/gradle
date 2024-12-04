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

import com.google.common.collect.Sets;
import groovy.lang.GroovyObject;
import groovy.lang.MissingPropertyException;
import groovy.util.ObservableMap;
import org.apache.tools.ant.MagicNames;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.PropertyHelper;
import org.apache.tools.ant.Target;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;
import org.gradle.api.internal.project.ant.BasicAntBuilder;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.ant.AntTarget;
import org.gradle.internal.Transformers;

import javax.annotation.Nullable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultAntBuilder extends BasicAntBuilder implements GroovyObject {

    private final Project gradleProject;
    private final AntLoggingAdapter loggingAdapter;

    public DefaultAntBuilder(Project gradleProject, AntLoggingAdapter loggingAdapter) {
        this.gradleProject = gradleProject;
        this.loggingAdapter = loggingAdapter;
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

    @Override
    public Map<String, Object> getProperties() {
        ObservableMap map = new ObservableMap(getProject().getProperties());
        map.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                doSetProperty(event.getPropertyName(), event.getNewValue());
            }
        });

        @SuppressWarnings("unchecked") Map<String, Object> castMap = (Map<String, Object>) map;
        return castMap;
    }

    @Override
    public Map<String, Object> getReferences() {
        ObservableMap map = new ObservableMap(getProject().getReferences());
        map.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                getProject().addReference(event.getPropertyName(), event.getNewValue());
            }
        });

        @SuppressWarnings("unchecked") Map<String, Object> castMap = (Map<String, Object>) map;
        return castMap;
    }

    @Override
    public void importBuild(Object antBuildFile) {
        importBuild(antBuildFile, Transformers.<String>noOpTransformer());
    }

    @Override
    public void importBuild(Object antBuildFile, String baseDirectory) {
        importBuild(antBuildFile, baseDirectory, Transformers.<String>noOpTransformer());
    }

    @Override
    public void importBuild(Object antBuildFile, Transformer<? extends String, ? super String> taskNamer) {
        importBuild(antBuildFile, null, taskNamer);
    }

    @Override
    public void importBuild(Object antBuildFile, String baseDirectory, Transformer<? extends String, ? super String> taskNamer) {
        File file = gradleProject.file(antBuildFile);

        Optional<Object> baseDirectoryOptional = Optional.ofNullable(baseDirectory);
        final File baseDir = gradleProject.file(baseDirectoryOptional.orElse(file.getParentFile().getAbsolutePath()));

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
            @SuppressWarnings("deprecation")
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
        List<String> taskDependencyNames = new LinkedList<>();
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
                    @Override
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

    @Override
    public void setLifecycleLogLevel(AntMessagePriority logLevel) {
        loggingAdapter.setLifecycleLogLevel(logLevel);
    }

    @Override
    public AntMessagePriority getLifecycleLogLevel() {
        return loggingAdapter.getLifecycleLogLevel();
    }

    private static class AntTargetsTaskDependency implements TaskDependencyInternal {
        private final List<String> taskDependencyNames;

        public AntTargetsTaskDependency(List<String> taskDependencyNames) {
            this.taskDependencyNames = taskDependencyNames;
        }

        @Override
        public Set<? extends Task> getDependenciesForInternalUse(@Nullable Task task) {
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

        @Override
        public Set<? extends Task> getDependencies(Task task) {
            return getDependenciesForInternalUse(task);
        }
    }
}
