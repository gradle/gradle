/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import org.gradle.tooling.model.gradle.BuildInvocations

@TargetGradleVersion('>=6.8')
class CompositeBuildTaskExecutionCrossVersionSpec extends ToolingApiSpecification {

    def "can run included root project task"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":other-build:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can run included root project task via launchable from GradleProject model"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaGradleProjectLaunchable("doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can run included root project task via launchable from BuildInvocations model"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaBuildInvocationsLaunchable(":doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can run included subproject task"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include 'sub'
        """
        file('other-build/sub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":other-build:sub:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can run included subproject task via launchable from GradleProject model"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include 'sub'
        """
        file('other-build/sub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaGradleProjectLaunchable("doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can pass options to task in included build"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething', MyTask)

            class MyTask extends DefaultTask {
                private String content = 'default content'

                @Option(option = "content", description = "Message to print")
                public void setContent(String content) {
                    this.content = content
                }

                @TaskAction
                public void run() {
                    println content
                }
            }
        """

        when:
        executeTaskViaTAPI(":other-build:doSomething", "--content", "do something")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can list tasks from included build"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                description = "Prints the message 'do something'"
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":other-build:tasks", "--all")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("doSomething - Prints the message 'do something'")
    }

    def "can run help from included build"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                description = "Prints the message 'do something'"
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI("help", "--task", ":other-build:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("Prints the message 'do something'")
    }

    def "can use pattern matching to address tasks"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                description = "Prints the message 'do something'"
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":other-build:dSo")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can run tasks from transitive included builds"() {
        given:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('other-build')
        """
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            includeBuild('../third-build')
        """
        file('third-build/settings.gradle') << """
            rootProject.name = 'third-build'
            include('sub')
        """

        file('third-build/sub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":third-build:sub:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    @TargetGradleVersion(">=7.6")
    def "included build name can use pattern matching to execute a task"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":oB:doSo")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")

        when:
        executeTaskViaTAPI("oB:doSo")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "gives reasonable error message when a task does not exist in the referenced included build"() {
        given:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('other-build')
        """
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
        """

        when:
        executeTaskViaTAPI(":other-build:nonexistent")

        then:
        def exception = thrown(Exception)
        exception.cause.message.containsIgnoreCase("task 'nonexistent' not found in project ':other-build'.")
    }

    def "gives reasonable error message when a project does not exist in the referenced included build"() {
        given:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('other-build')
        """
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
        """

        when:
        executeTaskViaTAPI(":other-build:sub:nonexistent")

        then:
        def exception = thrown(Exception)
        exception.cause.message.containsIgnoreCase("project 'sub' not found in project ':other-build'.")
    }

    def "handles overlapping names between composite and a subproject within the composite"() {
        given:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('lib')
        """
        file('lib/settings.gradle') << """
            include('lib')
        """
        file('lib/lib/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":lib:lib:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can launch test with test launcher via build operation"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include 'sub'
        """
        file('other-build/sub/build.gradle') << """
            plugins {
                id 'java-library'
            }

             ${mavenCentralRepository()}

             dependencies { testImplementation 'junit:junit:4.13' }
        """
        file("other-build/sub/src/test/java/MyIncludedTest.java") << """
            import org.junit.Test;
            import static org.junit.Assert.assertTrue;

            public class MyIncludedTest {

                @Test
                public void myTestMethod() {
                    assertTrue(true);
                }
            }
        """

        TestOperationCollector collector = new TestOperationCollector()
        withConnection { connection ->
            def build = connection.newBuild()
            build.addProgressListener(collector)
            build.forTasks(":other-build:sub:test").run()
        }
        TestOperationDescriptor descriptor = collector.descriptors.find { it.name == "myTestMethod" }

        when:
        withConnection { connection ->
            TestLauncher launcher = connection.newTestLauncher().withTests(descriptor)
            collectOutputs(launcher)
            launcher.run()
        }

        then:
        outputContains("BUILD SUCCESSFUL")
    }

    def "can launch test with test launcher via test filter targeting a specific task"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include 'sub'
        """
        file('other-build/sub/build.gradle') << """
            plugins {
                id 'java-library'
            }

             ${mavenCentralRepository()}

             dependencies { testImplementation 'junit:junit:4.13' }
        """
        file("other-build/sub/src/test/java/MyIncludedTest.java") << """
            import org.junit.Test;
            import static org.junit.Assert.assertTrue;

            public class MyIncludedTest {

                @Test
                public void myTestMethod() {
                    assertTrue(true);
                }
            }
        """

        when:
        withConnection { connection ->
            def testLauncher = connection.newTestLauncher()
            collectOutputs(testLauncher)
            testLauncher.withTaskAndTestClasses(":other-build:sub:test", ["MyIncludedTest"]).run()
        }

        then:
        outputContains("BUILD SUCCESSFUL")
    }

    private void executeTaskViaTAPI(String... tasks) {
        withConnection { connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.forTasks(tasks).run()
        }
    }

    private void executeTaskViaGradleProjectLaunchable(String taskName) {
        withConnection { connection ->
            def gradleProjects = connection.action(new LoadCompositeModel(GradleProject)).run()
            def launchables = findLaunchables(gradleProjects, taskName)
            assert launchables.size == 1
            def build = connection.newBuild()
            collectOutputs(build)
            build.forLaunchables(launchables[0]).run()
        }
    }

    private def findLaunchables(Collection<GradleProject> gradleProjects, String taskName) {
        collectGradleProjects(gradleProjects).collect { it.tasks }.flatten().findAll { GradleTask task -> task.path.contains(taskName) }
    }

    private def collectGradleProjects(Collection<GradleProject> projects, Collection<GradleProject> acc = []) {
        acc.addAll(projects)
        projects.each { collectGradleProjects(it.children, acc) }
        acc
    }

    private void executeTaskViaBuildInvocationsLaunchable(String taskName) {
        withConnection { connection ->
            Collection<BuildInvocations> buildInvocations = connection.action(new LoadCompositeModel(BuildInvocations)).run()
            def tasks = buildInvocations.collect { it.tasks }.flatten().findAll { it.path.contains(taskName) }
            assert tasks.size == 1
            def build = connection.newBuild()
            collectOutputs(build)
            build.forLaunchables(tasks[0]).run()
        }
    }

    private boolean outputContains(String expectedOutput) {
        return stdout.toString().contains(expectedOutput)
    }

    class TestOperationCollector implements ProgressListener {

        List<TestOperationDescriptor> descriptors = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof TestFinishEvent) {
                descriptors += ((TestFinishEvent) event).descriptor
            }
        }
    }
}
