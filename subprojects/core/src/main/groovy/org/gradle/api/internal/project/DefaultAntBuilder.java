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
import org.gradle.api.internal.project.ant.BasicAntBuilder;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.ant.AntTarget;

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
            Target target = getAntProject().getTargets().get(name);
            AntTarget task = gradleProject.getTasks().create(target.getName(), AntTarget.class);
            task.setTarget(target);
            task.setBaseDir(baseDir);
            addDependencyOrdering(target.getDependencies());
        }
    }

    private void addDependencyOrdering(Enumeration<String> dependencies) {
        TaskContainer tasks = gradleProject.getTasks();
        String previous = null;
        for (final String dependency : Collections.list(dependencies)) {
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

}
