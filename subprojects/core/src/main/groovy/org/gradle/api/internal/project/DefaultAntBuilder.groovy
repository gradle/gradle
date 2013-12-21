/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project

import org.apache.tools.ant.MagicNames
import org.apache.tools.ant.ProjectHelper
import org.apache.tools.ant.Target
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.project.ant.BasicAntBuilder
import org.gradle.api.tasks.ant.AntTarget
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeEvent
import org.apache.tools.ant.PropertyHelper

public class DefaultAntBuilder extends BasicAntBuilder {
    private final Project gradleProject

    def DefaultAntBuilder(Project gradleProject) {
        this.gradleProject = gradleProject;
    }

    def Object invokeMethod(String methodName, Object args) {
        super.invokeMethod(methodName, args)
    }

    def propertyMissing(String property, Object newValue) {
        doSetProperty(property, newValue)
    }

    private def doSetProperty(String property, newValue) {
        PropertyHelper.getPropertyHelper(project).setUserProperty(null, property, newValue)
    }

    def propertyMissing(String name) {
        if (project.properties.containsKey(name)) {
            return project.properties[name]
        }
        throw new MissingPropertyException(name, getClass())
    }

    public Map getProperties() {
        ObservableMap map = new ObservableMap(project.properties)
        map.addPropertyChangeListener({PropertyChangeEvent event -> doSetProperty(event.propertyName, event.newValue) } as PropertyChangeListener)
        map
    }

    public Map getReferences() {
        ObservableMap map = new ObservableMap(project.references)
        map.addPropertyChangeListener({PropertyChangeEvent event -> project.addReference(event.propertyName, event.newValue) } as PropertyChangeListener)
        map
    }

    public void importBuild(Object antBuildFile) {
        File file = gradleProject.file(antBuildFile)
        File baseDir = file.parentFile

        Set existingAntTargets = new HashSet(antProject.targets.keySet())
        File oldBaseDir = antProject.baseDir
        antProject.baseDir = baseDir
        try {
            antProject.setUserProperty(MagicNames.ANT_FILE, file.getAbsolutePath())
            ProjectHelper.configureProject(antProject, file)
        } catch(Exception e) {
            throw new GradleException("Could not import Ant build file '$file'.", e)
        } finally {
            antProject.baseDir = oldBaseDir
        }

        // Chuck away the implicit target. It has already been executed
        antProject.targets.remove('')

        // Add an adapter for each newly added target
        Set newAntTargets = new HashSet(antProject.targets.keySet())
        newAntTargets.removeAll(existingAntTargets)
        newAntTargets.each {name ->
            Target target = antProject.targets[name]
            AntTarget task = gradleProject.tasks.create(target.name, AntTarget)
            task.target = target
            task.baseDir = baseDir
        }
    }
}
