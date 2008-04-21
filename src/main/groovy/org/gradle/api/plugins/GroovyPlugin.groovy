/*
 * Copyright 2007 the original author or authors.
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
import org.gradle.api.tasks.compile.AntGroovyc
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc

/**
 * @author Hans Dockter
 * todo: put convention mapping into constants
 */
class GroovyPlugin extends JavaPlugin {

    void apply(Project project, PluginRegistry pluginRegistry, def convention = null) {
        GroovyConvention groovyConvention = new GroovyConvention(project)
        project.convention = groovyConvention
        pluginRegistry.getPlugin(JavaPlugin).apply(project, pluginRegistry, groovyConvention)

        project.ant.taskdef(name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc")
        project.ant.taskdef(name: "groovydoc", classname: "org.codehaus.groovy.ant.Groovydoc")

        configureCompile(project.createTask(JavaPlugin.COMPILE, dependsOn: JavaPlugin.RESOURCES, type: GroovyCompile, overwrite: true), groovyConvention, DefaultConventionsToPropertiesMapping.COMPILE).configure {
            antGroovyCompile = new AntGroovyc()
            conventionMapping.groovySourceDirs = {groovyConvention.groovySrcDirs}
        }
        configureTestCompile(project.createTask(JavaPlugin.TEST_COMPILE, dependsOn: JavaPlugin.TEST_RESOURCES, type: GroovyCompile, overwrite: true),
                project.task(JavaPlugin.COMPILE),
                groovyConvention,
                DefaultConventionsToPropertiesMapping.TEST_COMPILE).configure {
            antGroovyCompile = new AntGroovyc()
            conventionMapping.groovySourceDirs = {groovyConvention.groovyTestSrcDirs}
        }

        project.createTask(JavaPlugin.JAVADOC, (DefaultProject.TASK_OVERWRITE): true, type: Groovydoc).configure {
            conventionMapping.srcDirs = {groovyConvention.srcDirs + groovyConvention.groovySrcDirs}
            conventionMapping.destDir = {groovyConvention.javadocDir}
        }
    }
}
