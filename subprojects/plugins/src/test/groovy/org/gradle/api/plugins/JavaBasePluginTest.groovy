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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.ClassDirectoryBinarySpec
import org.gradle.language.java.JavaSourceSet
import org.gradle.language.jvm.JvmResourceSet
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.file.FileCollectionMatchers.sameCollection
import static org.gradle.util.WrapUtil.toLinkedSet

class JavaBasePluginTest extends Specification {
    @Rule
    public SetSystemProperties sysProperties = new SetSystemProperties()
    private final DefaultProject project = TestUtil.createRootProject()

    void appliesBasePluginsAndAddsConventionObject() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        project.plugins.hasPlugin(ReportingBasePlugin)
        project.plugins.hasPlugin(BasePlugin)
        project.plugins.hasPlugin(LegacyJavaComponentPlugin)
        project.convention.plugins.java instanceof JavaPluginConvention
    }

    void createsTasksAndAppliesMappingsForNewSourceSet() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')
        new TestFile(project.file("src/custom/java/File.java")) << "foo"
        new TestFile(project.file("src/custom/resouces/resource.txt")) << "foo"

        then:
        SourceSet set = project.sourceSets.custom
        set.java.srcDirs == toLinkedSet(project.file('src/custom/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/custom/resources'))
        set.output.classesDir == new File(project.buildDir, 'classes/custom')

        def processResources = project.tasks['processCustomResources']
        processResources.description == "Processes JVM resources 'custom:resources'."
        processResources instanceof Copy
        TaskDependencyMatchers.dependsOn().matches(processResources)
        processResources.destinationDir == project.sourceSets.custom.output.resourcesDir
        def resources = processResources.source
        resources.files == project.sourceSets.custom.resources.files

        def compileJava = project.tasks['compileCustomJava']
        compileJava.description == "Compiles Java source 'custom:java'."
        compileJava instanceof JavaCompile
        TaskDependencyMatchers.dependsOn().matches(compileJava)
        compileJava.classpath.is(project.sourceSets.custom.compileClasspath)
        compileJava.destinationDir == project.sourceSets.custom.output.classesDir

        def sources = compileJava.source
        sources.files == project.sourceSets.custom.java.files

        def classes = project.tasks['customClasses']
        classes.description == "Assembles classes 'custom'."
        classes instanceof DefaultTask
        TaskDependencyMatchers.dependsOn('processCustomResources', 'compileCustomJava').matches(classes)
        TaskDependencyMatchers.builtBy('customClasses').matches(project.sourceSets.custom.output)
    }

    void "wires generated resources task into classes task for sourceset"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')

        and:
        final someTask = project.task("someTask")
        project.sourceSets.custom.output.dir('some-dir', builtBy: someTask)

        then:
        def customClasses = project.tasks['customClasses']
        TaskDependencyMatchers.dependsOn('someTask', 'processCustomResources', 'compileCustomJava').matches(customClasses)
    }

    void tasksReflectChangesToSourceSetConfiguration() {
        def classesDir = project.file('target/classes')
        def resourcesDir = project.file('target/resources')

        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')
        project.sourceSets.custom.output.classesDir = classesDir
        project.sourceSets.custom.output.resourcesDir = resourcesDir

        then:
        def processResources = project.tasks['processCustomResources']
        processResources.destinationDir == resourcesDir

        def compileJava = project.tasks['compileCustomJava']
        compileJava.destinationDir == classesDir
    }

    void createsConfigurationsForNewSourceSet() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        def sourceSet = project.sourceSets.create('custom')

        then:
        def compile = project.configurations.customCompile
        compile.transitive
        !compile.visible
        compile.extendsFrom == [] as Set
        compile.description == "Compile classpath for source set 'custom'."

        and:
        def runtime = project.configurations.customRuntime
        runtime.transitive
        !runtime.visible
        runtime.extendsFrom == [compile] as Set
        runtime.description == "Runtime classpath for source set 'custom'."

        and:
        def runtimeClasspath = sourceSet.runtimeClasspath
        def compileClasspath = sourceSet.compileClasspath
        compileClasspath == compile
        runtimeClasspath sameCollection(sourceSet.output + runtime)
    }

    void appliesMappingsToTasksDefinedByBuildScript() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        def compile = project.task('customCompile', type: JavaCompile)
        compile.sourceCompatibility == project.sourceCompatibility.toString()

        def test = project.task('customTest', type: Test.class)
        test.workingDir == project.projectDir
        test.reports.junitXml.destination == project.testResultsDir
        test.reports.html.destination == project.testReportDir
        test.reports.junitXml.enabled
        test.reports.html.enabled

        def javadoc = project.task('customJavadoc', type: Javadoc)
        javadoc.destinationDir == project.file("$project.docsDir/javadoc")
        javadoc.title == project.extensions.getByType(ReportingExtension).apiDocTitle
    }

    void appliesMappingsToCustomJarTasks() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        def task = project.task('customJar', type: Jar)

        then:
        TaskDependencyMatchers.dependsOn().matches(task)
        task.destinationDir == project.libsDir
    }

    void createsLifecycleBuildTasks() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        def build = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.CHECK_TASK_NAME, BasePlugin.ASSEMBLE_TASK_NAME).matches(build)

        def buildDependent = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME).matches(buildDependent)

        def buildNeeded = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME).matches(buildNeeded)
    }

    def configuresTestTaskWhenDebugSystemPropertyIsSet() {
        project.pluginManager.apply(JavaBasePlugin)
        def task = project.tasks.create('test', Test.class)

        when:
        System.setProperty("test.debug", "true")
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)

        then:
        task.debug
    }

    def "configures test task when test.single is used"() {
        project.pluginManager.apply(JavaBasePlugin)
        def task = project.tasks.create('test', Test.class)
        task.include 'ignoreme'

        when:
        System.setProperty("test.single", "pattern")
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)

        then:
        task.includes == ['**/pattern*.class'] as Set
        task.inputs.getSourceFiles().empty
    }

    def "adds language source sets for each source set added to the 'sourceSets' container"() {
        project.pluginManager.apply(JavaBasePlugin)

        when:
        project.sourceSets {
            custom {
                java {
                    srcDirs = [project.file("src1"), project.file("src2")]
                }
                resources {
                    srcDirs = [project.file("resrc1"), project.file("resrc2")]
                }
                compileClasspath = project.files("jar1.jar", "jar2.jar")
            }
        }

        then:
        project.sources.size() == 2

        and:
        def java = project.sources.withType(JavaSourceSet).iterator().next()
        java.source.srcDirs as Set == [project.file("src1"), project.file("src2")] as Set
        java.compileClasspath.files as Set == project.files("jar1.jar", "jar2.jar") as Set

        and:
        def resources = project.sources.withType(JvmResourceSet).iterator().next()
        resources.source.srcDirs as Set == [project.file("resrc1"), project.file("resrc2")] as Set
    }

    def "adds a class directory binary for each source set added to the 'sourceSets' container"() {
        project.pluginManager.apply(JavaBasePlugin)

        when:
        project.sourceSets {
            custom {
                output.classesDir = project.file("classes")
                output.resourcesDir = project.file("resources")
            }
        }

        then:
        def binary = project.binaries.findByName("customClasses")
        binary instanceof ClassDirectoryBinarySpec
        binary.classesDir == project.file("classes")
        binary.resourcesDir == project.file("resources")
        binary.inputs as Set == project.sources as Set
    }
}
