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


 
package org.gradle.api.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.HelperUtil
import org.gradle.util.Matchers
import spock.lang.Specification
import static org.gradle.util.WrapUtil.toLinkedSet
import org.gradle.api.tasks.testing.Test
import org.gradle.util.SetSystemProperties
import org.junit.Rule

/**
 * @author Hans Dockter
 */

class JavaBasePluginTest extends Specification {
    @Rule
    public SetSystemProperties sysProperties = new SetSystemProperties()
    private final Project project = HelperUtil.createRootProject()
    private final JavaBasePlugin javaBasePlugin = new JavaBasePlugin()

    void appliesBasePluginsAndAddsConventionObject() {
        when:
        javaBasePlugin.apply(project)

        then:
        project.getPlugins().hasPlugin(ReportingBasePlugin)
        project.getPlugins().hasPlugin(BasePlugin)
        project.convention.plugins.java instanceof JavaPluginConvention
    }

    void createsTasksAndAppliesMappingsForNewSourceSet() {
        when:
        javaBasePlugin.apply(project)
        project.sourceSets.add('custom')
        
        then:
        def set = project.sourceSets.custom
        set.java.srcDirs == toLinkedSet(project.file('src/custom/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/custom/resources'))
        set.classesDir == new File(project.buildDir, 'classes/custom')
        Matchers.builtBy('customClasses').matches(set.classes)

        def task = project.tasks['processCustomResources']
        task.description == 'Processes the custom resources.'
        task instanceof Copy
        Matchers.dependsOn().matches(task)
        task.destinationDir == project.sourceSets.custom.classesDir
        task.defaultSource == project.sourceSets.custom.resources

        task = project.tasks['compileCustomJava']
        task.description == 'Compiles the custom Java source.'
        task instanceof Compile
        Matchers.dependsOn().matches(task)
        task.defaultSource == project.sourceSets.custom.java
        task.classpath.is(project.sourceSets.custom.compileClasspath)
        task.destinationDir == project.sourceSets.custom.classesDir

        task = project.tasks['customClasses']
        task.description == 'Assembles the custom classes.'
        task instanceof DefaultTask
        Matchers.dependsOn('processCustomResources', 'compileCustomJava').matches(task)
    }

    void appliesMappingsToTasksDefinedByBuildScript() {
        when:
        javaBasePlugin.apply(project)
        def task = project.createTask('customCompile', type: Compile)

        then:
        task.sourceCompatibility == project.sourceCompatibility.toString()

        task = project.createTask('customTest', type: Test.class)
        task.workingDir == project.projectDir
        task.testResultsDir == project.testResultsDir
        task.testReportDir == project.testReportDir

        task = project.createTask('customJavadoc', type: Javadoc)
        task.destinationDir == project.file("$project.docsDir/javadoc")
        task.title == project.apiDocTitle
    }

    void appliesMappingsToCustomJarTasks() {
        when:
        javaBasePlugin.apply(project)
        def task = project.createTask('customJar', type: Jar)

        then:
        Matchers.dependsOn().matches(task)
        task.destinationDir == project.libsDir
    }

    void createsLifecycleBuildTasks() {
        when:
        javaBasePlugin.apply(project)

        then:
        def build = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]
        Matchers.dependsOn(JavaBasePlugin.CHECK_TASK_NAME, BasePlugin.ASSEMBLE_TASK_NAME).matches(build)

        def buildDependent = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]
        Matchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME).matches(buildDependent)

        def buildNeeded = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]
        Matchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME).matches(buildNeeded)
    }

    def configuresTestTaskWhenDebugSystemPropertyIsSet() {
        javaBasePlugin.apply(project)
        def task = project.tasks.add('test', Test.class)

        when:
        System.setProperty("test.debug", "true")
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)

        then:
        task.debug
    }

    def configuresTestTaskWhenSingleTestSystemPropertyIsSet() {
        javaBasePlugin.apply(project)
        def task = project.tasks.add('test', Test.class)
        task.include 'ignoreme'

        when:
        System.setProperty("test.single", "pattern")
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)

        then:
        task.includes == ['**/pattern*.class'] as Set
    }

}