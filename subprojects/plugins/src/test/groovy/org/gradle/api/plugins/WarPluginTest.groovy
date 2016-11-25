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

package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.bundling.War
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn

class WarPluginTest extends AbstractProjectBuilderSpec {
    private final WarPlugin warPlugin = new WarPlugin()

    def "applies Java plugin and adds convention"() {
        when:
        warPlugin.apply(project)

        then:
        project.getPlugins().hasPlugin(JavaPlugin)
        project.convention.plugins.war instanceof WarPluginConvention
    }

    def "creates configurations"() {
        given:
        warPlugin.apply(project)

        when:
        def providedCompileConfiguration = project.configurations.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)

        then:
        providedCompileConfiguration.extendsFrom  == [] as Set
        !providedCompileConfiguration.visible
        providedCompileConfiguration.transitive

        when:
        def compileConfiguration = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)

        then:
        compileConfiguration.extendsFrom  == [providedCompileConfiguration] as Set
        !compileConfiguration.visible
        compileConfiguration.transitive

        when:
        def providedRuntimeConfiguration = project.configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)

        then:
        providedRuntimeConfiguration.extendsFrom == [providedCompileConfiguration] as Set
        !providedRuntimeConfiguration.visible
        providedRuntimeConfiguration.transitive

        when:
        def runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)

        then:
        runtimeConfiguration.extendsFrom == [compileConfiguration, providedRuntimeConfiguration] as Set
        !runtimeConfiguration.visible
        runtimeConfiguration.transitive


    }

    def "adds tasks"() {
        when:
        warPlugin.apply(project)

        then:
        def task = project.tasks[WarPlugin.WAR_TASK_NAME]
        task instanceof War
        dependsOn(JavaPlugin.CLASSES_TASK_NAME).matches(task)
        task.destinationDir == project.libsDir

        when:
        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]

        then:
        dependsOn(WarPlugin.WAR_TASK_NAME).matches(task)
    }

    def "depends on runtime config"() {
        given:
        warPlugin.apply(project)

        when:
        Project childProject = TestUtil.createChildProject(project, 'child')
        JavaPlugin javaPlugin = new JavaPlugin()
        javaPlugin.apply(childProject)

        project.dependencies {
            runtime project(path: childProject.path, configuration: 'archives')
        }

        then:
        def task = project.tasks[WarPlugin.WAR_TASK_NAME]
        ':child:jar' in task.taskDependencies.getDependencies(task)*.path
    }

    def "uses runtime classpath excluding provided as classpath"() {
        given:
        File compileJar = project.file('compile.jar')
        File compileOnlyJar = project.file('compileOnly.jar')
        File runtimeJar = project.file('runtime.jar')
        File providedJar = project.file('provided.jar')

        warPlugin.apply(project)

        when:
        project.dependencies {
            providedCompile project.files(providedJar)
            compile project.files(compileJar)
            compileOnly project.files(compileOnlyJar)
            runtime project.files(runtimeJar)
        }

        then:
        def task = project.tasks[WarPlugin.WAR_TASK_NAME]
        task.classpath.files as List == [project.sourceSets.main.output.classesDir, project.sourceSets.main.output.resourcesDir, compileJar, runtimeJar]
    }

    def "applies mappings to archive tasks"() {
        warPlugin.apply(project)

        when:
        def task = project.task('customWar', type: War)

        then:
        dependsOn(JavaPlugin.CLASSES_TASK_NAME).matches(task)
        task.destinationDir == project.libsDir
    }

    def "replaces jar as publication"() {
        given:
        warPlugin.apply(project)

        when:
        def archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)

        then:
        archiveConfiguration.getAllArtifacts().size() == 1
        archiveConfiguration.getAllArtifacts()[0].type == "war"
    }
}
