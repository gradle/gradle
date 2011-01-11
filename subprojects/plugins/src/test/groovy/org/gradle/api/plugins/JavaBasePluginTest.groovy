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

        def processResources = project.tasks['processCustomResources']
        processResources.description == 'Processes the custom resources.'
        processResources instanceof Copy
        Matchers.dependsOn().matches(processResources)
        processResources.destinationDir == project.sourceSets.custom.classesDir
        processResources.defaultSource == project.sourceSets.custom.resources

        def compileJava = project.tasks['compileCustomJava']
        compileJava.description == 'Compiles the custom Java source.'
        compileJava instanceof Compile
        Matchers.dependsOn().matches(compileJava)
        compileJava.defaultSource == project.sourceSets.custom.java
        compileJava.classpath.is(project.sourceSets.custom.compileClasspath)
        compileJava.destinationDir == project.sourceSets.custom.classesDir

        def classes = project.tasks['customClasses']
        classes.description == 'Assembles the custom classes.'
        classes instanceof DefaultTask
        Matchers.dependsOn('processCustomResources', 'compileCustomJava').matches(classes)
    }

    void appliesMappingsToTasksDefinedByBuildScript() {
        when:
        javaBasePlugin.apply(project)

        then:
        def compile = project.createTask('customCompile', type: Compile)
        compile.sourceCompatibility == project.sourceCompatibility.toString()

        def test = project.createTask('customTest', type: Test.class)
        test.workingDir == project.projectDir
        test.testResultsDir == project.testResultsDir
        test.testReportDir == project.testReportDir

        def javadoc = project.createTask('customJavadoc', type: Javadoc)
        javadoc.destinationDir == project.file("$project.docsDir/javadoc")
        javadoc.title == project.apiDocTitle
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