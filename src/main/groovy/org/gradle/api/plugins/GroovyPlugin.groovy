/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.Configuration.Visibility

/**
 * @author Hans Dockter
 * todo: put convention mapping into constants
 */
class GroovyPlugin extends JavaPlugin {
    static final String GROOVY = 'groovy'

    void apply(Project project, PluginRegistry pluginRegistry, Map customValues) {
        pluginRegistry.apply(JavaPlugin, project, pluginRegistry, customValues)
        GroovyPluginConvention groovyPluginConvention = new GroovyPluginConvention(project, customValues)
        project.convention.plugins.groovy = groovyPluginConvention
        groovyPluginConvention.groovyClasspath = {project.dependencies.resolve('groovy')}
        configureCompile(project.createTask(JavaPlugin.COMPILE, dependsOn: JavaPlugin.RESOURCES, type: GroovyCompile,
                overwrite: true), DefaultConventionsToPropertiesMapping.COMPILE).configure {
            conventionMapping.groovySourceDirs = {groovyPluginConvention.groovySrcDirs}
            conventionMapping.groovyClasspath = {it.project.dependencies.resolve('compile')}
        }
        configureTestCompile(project.createTask(JavaPlugin.TEST_COMPILE, dependsOn: JavaPlugin.TEST_RESOURCES,
                type: GroovyCompile, overwrite: true),
                project.task(JavaPlugin.COMPILE),
                DefaultConventionsToPropertiesMapping.TEST_COMPILE).configure {
            conventionMapping.groovySourceDirs = {groovyPluginConvention.groovyTestSrcDirs}
            conventionMapping.groovyClasspath = {it.project.dependencies.resolve('testCompile')}
        }

        project.createTask(JavaPlugin.JAVADOC, (DefaultProject.TASK_OVERWRITE): true, type: Groovydoc).configure {
            conventionMapping.srcDirs = {project.convention.plugins.java.srcDirs + groovyPluginConvention.groovySrcDirs}
            conventionMapping.destDir = {project.convention.plugins.java.javadocDir}
        }

        project.dependencies {
            addConfiguration(new Configuration(GROOVY, Visibility.PRIVATE, null, null, false, null))
            addConfiguration(new Configuration(JavaPlugin.COMPILE, Visibility.PRIVATE, null, [GROOVY] as String[], false, null))
        }
    }
}
