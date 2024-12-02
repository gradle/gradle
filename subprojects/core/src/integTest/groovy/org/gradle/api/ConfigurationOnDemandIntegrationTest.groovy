/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.ProjectLifecycleFixture
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule
import spock.lang.Issue

@FluidDependenciesResolveTest
class ConfigurationOnDemandIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    ProjectLifecycleFixture fixture = new ProjectLifecycleFixture(executer, temporaryFolder)

    def setup() {
        file("gradle.properties") << "org.gradle.configureondemand=true"
    }

    @Requires(
        value = [IntegTestPreconditions.NotParallelExecutor, IntegTestPreconditions.NotIsolatedProjects],
        reason = "these features hide incubating message"
    )
    def "presents incubating message"() {
        file("gradle.properties") << "org.gradle.configureondemand=false"
        buildFile << "task foo"

        when:
        run("foo", "--configure-on-demand")

        then:
        fixture.assertProjectsConfigured(":")
        output.count("Configuration on demand is an incubating feature") == 1
    }

    @Requires(
        value = [IntegTestPreconditions.NotParallelExecutor, IntegTestPreconditions.NotIsolatedProjects],
        reason = "these features hide incubating message"
    )
    def "presents incubating message with parallel mode"() {
        file("gradle.properties") << "org.gradle.configureondemand=false"
        buildFile << "task foo"

        when:
        run("foo", "--configure-on-demand", "--parallel")

        then:
        fixture.assertProjectsConfigured(":")
        output.count("Configuration on demand is an incubating feature") == 1
    }

    def "can be enabled from command line for a single module build"() {
        file("gradle.properties") << "org.gradle.configureondemand=false"
        buildFile << "task foo"

        when:
        run("foo", "--configure-on-demand")

        then:
        fixture.assertProjectsConfigured(":")
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects")
    def "evaluates only project referenced in the task list"() {
        createDirs("api", "impl", "util", "util/impl")
        settingsFile << "include 'api', 'impl', 'util', 'util:impl'"
        buildFile << "allprojects { task foo }"

        when:
        run(":util:impl:foo")

        then:
        fixture.assertProjectsConfigured(":", ":util", ":util:impl")
    }

    def "does not show configuration on demand incubating message in a regular mode"() {
        file("gradle.properties").text = "org.gradle.configureondemand=false"
        when:
        run()
        then:
        !output.contains("Configuration on demand is incubating")
    }

    @ToBeFixedForConfigurationCache(because = "test expects configuration phase")
    def "follows java project dependencies"() {
        createDirs("api", "impl", "util")
        settingsFile << "include 'api', 'impl', 'util'"
        buildFile << "allprojects { apply plugin: 'java-library' } "

        file("impl/build.gradle") << "dependencies { api project(':api') } "
        file("util/build.gradle") << "dependencies { implementation project(':impl') } "
        //util -> impl -> api

        file("api/src/main/java/Person.java") << """public interface Person {
    String getName();
}
"""
        file("impl/src/main/java/PersonImpl.java") << """public class PersonImpl implements Person {
    public String getName() {
        return "Szczepan";
    }
}
"""
        file("util/src/main/java/Utility.java") << "public class Utility extends PersonImpl {}"

        when:
        run(":api:build")

        then:
        fixture.assertProjectsConfigured(":", ":api")

        when:
        inDirectory("impl")
        run(":api:build")

        then:
        fixture.assertProjectsConfigured(":", ":api")

        when:
        run(":impl:build")

        then:
        fixture.assertProjectsConfigured(":", ":impl", ":api")

        when:
        run(":util:build")

        then:
        fixture.assertProjectsConfigured(":", ":util", ":impl", ":api")
    }

    @ToBeFixedForIsolatedProjects(because = "configure-on-demand is not supported in IP mode")
    def "can have cycles in project dependencies"() {
        settingsFile << "include 'api', 'impl', 'util'"
        file("util/build.gradle") << """
            plugins {
                id("java-library")
            }
        """
        file("impl/build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation project(path: ':api', configuration: 'other')
            }
        """
        file("api/build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                runtimeOnly project(':impl')
            }
            configurations {
                create("other")
            }
            task run {
                dependsOn(configurations.runtimeClasspath)
            }
        """
        buildFile << """
            plugins {
                id("java-library")
            }
        """

        when:
        run(":api:run")

        then:
        fixture.assertProjectsConfigured(":", ":api", ':impl')
    }

    @ToBeFixedForIsolatedProjects(because = "Property dynamic lookup")
    def "follows project dependencies when run in subproject"() {
        createDirs("api", "impl", "util")
        settingsFile << "include 'api', 'impl', 'util'"

        file("api/build.gradle") << "configurations { api }"
        file("impl/build.gradle") << """
            configurations { util }
            dependencies { util project(path: ':api', configuration: 'api') }
            task build(dependsOn: configurations.util)
        """

        when:
        inDirectory("impl")
        run("build")

        then:
        fixture.assertProjectsConfigured(':', ':impl', ':api')
    }

    @ToBeFixedForIsolatedProjects(because = "configure-on-demand is not supported in IP mode")
    def "name matching execution from root evaluates all projects"() {
        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'"
        buildFile << "task foo"

        when:
        run("foo")

        then:
        fixture.assertProjectsConfigured(":", ":api", ":impl")

        when:
        run(":foo")

        then:
        fixture.assertProjectsConfigured(":")
    }

    @ToBeFixedForIsolatedProjects(because = "configure-on-demand is not supported in IP mode")
    def "name matching execution from subproject evaluates only the subproject recursively"() {
        createDirs("api", "impl", "impl/one", "impl/two", "impl/two/abc")
        settingsFile << "include 'api', 'impl:one', 'impl:two', 'impl:two:abc'"
        file("impl/build.gradle") << "task foo"

        when:
        inDirectory("impl")
        run("foo")

        then:
        fixture.assertProjectsConfigured(":", ":impl", ":impl:one", ":impl:two", ":impl:two:abc")
    }

    @ToBeFixedForIsolatedProjects(because = "configure-on-demand is not supported in IP mode")
    def "may run implicit tasks from root"() {
        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'"

        when:
        run(":help")

        then:
        fixture.assertProjectsConfigured(":")
    }

    @ToBeFixedForIsolatedProjects(because = "configure-on-demand is not supported in IP mode")
    def "may run implicit tasks for subproject"() {
        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'"

        when:
        run(":api:help")

        then:
        fixture.assertProjectsConfigured(":", ":api")
    }

    @ToBeFixedForIsolatedProjects(because = "configure-on-demand is not supported in IP mode")
    def "respects default tasks"() {
        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'"
        file("api/build.gradle") << """
            task foo
            defaultTasks 'foo'
        """

        when:
        inDirectory('api')
        run()

        then:
        fixture.assertProjectsConfigured(":", ":api")
        result.assertTasksExecuted(':api:foo')
    }

    @ToBeFixedForIsolatedProjects(because = "evaluationDependsOn is not IP compatible")
    def "respects evaluationDependsOn"() {
        createDirs("api", "impl", "other")
        settingsFile << "include 'api', 'impl', 'other'"
        file("api/build.gradle") << """
            evaluationDependsOn(":impl")
        """

        when:
        run("api:help")

        then:
        fixture.assertProjectsConfigured(":", ":impl", ":api")
    }

    @ToBeFixedForConfigurationCache(because = "test expects configuration phase")
    def "respects buildProjectDependencies setting"() {
        createDirs("api", "impl", "other")
        settingsFile << "include 'api', 'impl', 'other'"
        file("impl/build.gradle") << """
            apply plugin: 'java-library'
            dependencies { implementation project(":api") }
        """
        file("api/build.gradle") << "apply plugin: 'java'"
        // Provide a source file so that the compile task doesn't skip resolving inputs
        file("impl/src/main/java/Foo.java") << "public class Foo {}"

        when:
        run("impl:build")

        then:
        executed ":api:jar", ":impl:jar"
        fixture.assertProjectsConfigured(":", ":impl", ":api")

        when:
        run("impl:build", "--no-rebuild") // impl -> api

        then:
        executed ":impl:jar"
        notExecuted ":api:jar"
        // :api is configured to resolve impl.compileClasspath configuration
        fixture.assertProjectsConfigured(":", ":impl", ":api")
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects")
    def "respects external task dependencies"() {
        createDirs("api", "impl", "other")
        settingsFile << "include 'api', 'impl', 'other'"
        file("build.gradle") << "allprojects { task foo }"
        file("impl/build.gradle") << """
            task bar(dependsOn: ":api:foo")
        """

        when:
        run("impl:bar")

        then:
        fixture.assertProjectsConfigured(":", ":impl", ":api")
        result.assertTasksExecutedInOrder(":api:foo", ":impl:bar")
    }

    def "supports buildSrc"() {
        file("buildSrc/src/main/java/FooTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class FooTask extends DefaultTask {
                @TaskAction public void logStuff(){
                    System.out.println(String.format("Horray!!! '%s' executed.", getName()));
                }
            }
        """

        buildFile << "task foo(type: FooTask)"

        when:
        run("foo", "-s")
        then:
        output.contains "Horray!!!"
    }

    @UnsupportedWithConfigurationCache(because = "runs configuration at execution time")
    def "may configure project at execution time"() {
        createDirs("a", "b", "c")
        settingsFile << "include 'a', 'b', 'c'"
        file('a/build.gradle') << """
            configurations { conf }
            dependencies { conf project(path: ":b", configuration: "conf") }
            task resolveConf {
                doLast {
                    //resolves at execution time, forcing 'b' to get configured
                    configurations.conf.files
                }
            }
        """

        file('b/build.gradle') << """
            configurations { conf }
        """

        when:
        run(":a:resolveConf", "-i")

        then:
        fixture.assertProjectsConfigured(":", ":a", ":b")
    }

    @ToBeFixedForIsolatedProjects(because = "configure-on-demand is not supported in IP mode")
    def "handles buildNeeded"() {
        createDirs("a", "b", "c")
        settingsFile << "include 'a', 'b', 'c'"
        file("a/build.gradle") << """ apply plugin: 'java' """
        file("b/build.gradle") << """
            apply plugin: 'java'
            project(':b') {
                dependencies { implementation project(':a') }
            }
        """

        when:
        run(":b:buildNeeded")

        then:
        executed ':b:buildNeeded', ':a:buildNeeded'
        fixture.assertProjectsConfigured(":", ":b", ":a")
    }

    @ToBeFixedForIsolatedProjects(because = "buildDependents is not IP compatible, configure projects from root,")
    def "handles buildDependents"() {
        createDirs("a", "b", "c")
        settingsFile << "include 'a', 'b', 'c'"
        file("a/build.gradle") << """ apply plugin: 'java' """
        file("b/build.gradle") << """
            apply plugin: 'java'
            project(':b') {
                dependencies { implementation project(':a') }
            }
        """

        when:
        run(":a:buildDependents")

        then:
        executed ':b:buildDependents', ':a:buildDependents'
        //unfortunately buildDependents requires all projects to be configured
        fixture.assertProjectsConfigured(":", ":a", ":b", ":c")
    }

    @ToBeFixedForIsolatedProjects(because = "configure-on-demand is not supported in IP mode")
    def "task command-line argument may look like a task path"() {
        createDirs("a", "b", "c")
        settingsFile << "include 'a', 'b', 'c'"
        file("a/build.gradle") << """
task one(type: SomeTask)
task two(type: SomeTask)

class SomeTask extends DefaultTask {
    @org.gradle.api.tasks.options.Option(description="some value")
    @Input
    String value
}
"""

        when:
        run(":a:one", "--value", ":b:thing", "a:two", "--value", "unknown:unknown")

        then:
        result.assertTasksExecuted(":a:one", ":a:two")
        fixture.assertProjectsConfigured(":", ":a")
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects")
    def "does not configure all projects when excluded task path is not qualified and is exact match for task in default project"() {
        createDirs("a", "a/child", "b", "b/child", "c")
        settingsFile << "include 'a', 'a:child', 'b', 'b:child', 'c'"
        file('a').mkdirs()
        file('b').mkdirs()
        buildFile << """
allprojects {
    task one
    task two
    task three
}
"""

        when:
        run(":a:one", "-x", "two", "-x", "three")

        then:
        result.assertTasksExecuted(":a:one")
        fixture.assertProjectsConfigured(":", ":a")

        when:
        executer.usingProjectDirectory(file('a'))
        run(":a:one", "-x", "two", "-x", "three")

        then:
        result.assertTasksExecuted(":a:one")
        fixture.assertProjectsConfigured(":", ":a")

        when:
        executer.usingProjectDirectory(file('b'))
        run(":a:one", "-x", "two", "-x", "three")

        then:
        result.assertTasksExecuted(":a:one")
        fixture.assertProjectsConfigured(":", ":a", ":b")
    }

    @Issue("https://github.com/gradle/gradle/issues/29154")
    @ToBeFixedForIsolatedProjects(because = "-x is not IP compatible")
    def "does not configure all projects when excluded task path is not qualified and an exact match for task has already been seen in some sub-project of default project"() {
        createDirs("a", "b", "c", "c/child")
        settingsFile << "include 'a', 'b', 'c', 'c:child'"
        file('c').mkdirs()
        buildFile << """
allprojects {
    task one
}
project(':b') {
    task two
}
"""

        when:
        run(":a:one", "-x", "two")

        then:
        result.assertTasksExecuted(":a:one")
        fixture.assertProjectsConfigured(":", ":a")

        when:
        executer.usingProjectDirectory(file("c"))
        runAndFail(":a:one", "-x", "two")

        then:
        failure.assertHasDescription("Task 'two' not found in project ':c' and its subprojects.")
        fixture.assertProjectsConfigured(":", ":a", ":c", ':c:child')
    }

    @Issue("https://github.com/gradle/gradle/issues/29154")
    @ToBeFixedForIsolatedProjects(because = "-x is not IP compatible")
    def "configures all subprojects of default project when excluded task path is not qualified and an exact match not found in default project"() {
        createDirs("a", "b", "c", "c/child")
        settingsFile << "include 'a', 'b', 'c', 'c:child'"
        file('c').mkdirs()
        buildFile << """
allprojects {
    task one
}
"""
        file("b/build.gradle") << "task two"

        when:
        run(":a:one", "-x", "two")

        then:
        result.assertTasksExecuted(":a:one")
        fixture.assertProjectsConfigured(":", ":a", ":b", ":c", ":c:child")

        when:
        executer.usingProjectDirectory(file("c"))
        runAndFail(":a:one", "-x", "two")

        then:
        failure.assertHasDescription("Task 'two' not found in project ':c' and its subprojects.")
        fixture.assertProjectsConfigured(":", ":a", ":c", ':c:child')
    }

    @Issue("https://github.com/gradle/gradle/issues/29154")
    @ToBeFixedForIsolatedProjects(because = "-x is not IP compatible")
    def "configures all subprojects of default projects when excluded task path is not qualified and uses camel case matching"() {
        createDirs("a", "b", "b/child", "c")
        settingsFile << "include 'a', 'b', 'b:child', 'c'"
        file('b').mkdirs()
        buildFile << """
allprojects {
    task one
    task two
}
"""

        when:
        run(":a:one", "-x", "tw")

        then:
        result.assertTasksExecuted(":a:one")
        fixture.assertProjectsConfigured(":", ":a", ":b", ":c", ":b:child")

        when:
        executer.usingProjectDirectory(file('b'))
        run(":a:one", "-x", "tw")

        then:
        result.assertTasksExecuted(":a:one")
        fixture.assertProjectsConfigured(":", ":a", ":b", ":b:child")
    }

    @ToBeFixedForIsolatedProjects(because = "reaches out to a property of the parent project")
    def "extra properties defined in parent project are accessible to child"() {
        createDirs("a", "a/child")
        settingsFile << "include 'a', 'a:child'"
        buildFile('a/build.gradle', """
            ext.foo = "Moo!!!"
        """)
        buildFile('a/child/build.gradle', """
            task printExt {
                def foo = foo
                doLast {
                    println "The Foo says " + foo
                }
            }
        """)
        when:
        run(":a:child:printExt")

        then:
        outputContains("The Foo says Moo!!!")
    }

    @ToBeFixedForConfigurationCache(because = "test expects configuration phase on second run")
    @Issue("https://github.com/gradle/gradle/issues/18460")
    @IntegrationTestTimeout(value = 60, onlyIf = { GradleContextualExecuter.embedded })
    def "can query dependencies with configure on demand enabled"() {
        def subprojects = ["a", "b"]
        multiProjectBuild("outputRegistry", subprojects)
        subprojects.each { projectName ->
            file("${projectName}/build.gradle") << """
                plugins {
                    id("java-library")
                }
            """
        }
        file("a/build.gradle") << """
            dependencies {
                implementation(project(":b"))
            }
        """

        when:
        succeeds("a:dependencies", "--configure-on-demand", "--debug")
        then:
        outputContains("More outputs are being registered even though the build output cleanup registry has already been finalized.")

        expect:
        succeeds("a:dependencies", "--configure-on-demand")
    }
}
