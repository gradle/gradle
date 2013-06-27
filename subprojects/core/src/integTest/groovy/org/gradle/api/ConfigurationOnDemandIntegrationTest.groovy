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
import org.gradle.integtests.fixtures.executer.ProjectLifecycleFixture
import org.junit.Rule

/**
 * by Szczepan Faber, created at: 11/21/12
 */
class ConfigurationOnDemandIntegrationTest extends AbstractIntegrationSpec {

    @Rule ProjectLifecycleFixture fixture = new ProjectLifecycleFixture(executer, temporaryFolder)

    def setup() {
        file("gradle.properties") << "org.gradle.configureondemand=true"
    }

    def "can be enabled from command line for a single module build"() {
        file("gradle.properties") << "org.gradle.configureondemand=false"
        buildFile << "task foo"

        when:
        run("foo", "--configure-on-demand")

        then:
        fixture.assertProjectsConfigured(":")
        assert output.count("Configuration on demand is incubating") == 1
    }

    def "evaluates only project referenced in the task list"() {
        settingsFile << "include 'api', 'impl', 'util', 'util:impl'"
        buildFile << "allprojects { task foo }"

        when:
        run(":foo", ":util:impl:foo")

        then:
        fixture.assertProjectsConfigured(":", ":util:impl")
    }

    def "does not show configuration on demand incubating message in a regular mode"() {
        file("gradle.properties").text = "org.gradle.configureondemand=false"
        when:
        run()
        then:
        assert !output.contains("Configuration on demand is incubating")
    }

    def "follows java project dependencies"() {
        settingsFile << "include 'api', 'impl', 'util'"
        buildFile << "allprojects { apply plugin: 'java' } "

        file("impl/build.gradle") << "dependencies { compile project(':api') } "
        file("util/build.gradle") << "dependencies { compile project(':impl') } "
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

    def "can have cycles in project dependencies"() {
        settingsFile << "include 'api', 'impl', 'util'"
        buildFile << """
allprojects { apply plugin: 'java' }
project(':impl') {
    dependencies { compile project(path: ':api', configuration: 'archives') }
}
project(':api') {
    dependencies { runtime project(':impl') }
    task run(dependsOn: configurations.runtime)
}
"""

        when:
        run(":api:run")

        then:
        fixture.assertProjectsConfigured(":", ":api", ':impl')
    }

    def "follows project dependencies when ran in subproject"() {
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

    def "name matching execution from root evaluates all projects"() {
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

    def "name matching execution from subproject evaluates only the subproject recursively"() {
        settingsFile << "include 'api', 'impl:one', 'impl:two', 'impl:two:abc'"
        file("impl/build.gradle") << "task foo"

        when:
        inDirectory("impl")
        run("foo")

        then:
        fixture.assertProjectsConfigured(":", ":impl", ":impl:one", ":impl:two", ":impl:two:abc")
    }

    def "may run implicit tasks from root"() {
        settingsFile << "include 'api', 'impl'"

        when:
        run(":tasks")

        then:
        fixture.assertProjectsConfigured(":")
    }

    def "may run implicit tasks for subproject"() {
        settingsFile << "include 'api', 'impl'"

        when:
        run(":api:tasks")

        then:
        fixture.assertProjectsConfigured(":", ":api")
    }

    def "respects default tasks"() {
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

    def "respects evaluationDependsOn"() {
        settingsFile << "include 'api', 'impl', 'other'"
        file("api/build.gradle") << """
            evaluationDependsOn(":impl")
        """

        when:
        run("api:tasks")

        then:
        fixture.assertProjectsConfigured(":", ":impl", ":api")
    }

    def "respects buildProjectDependencies setting"() {
        settingsFile << "include 'api', 'impl', 'other'"
        file("impl/build.gradle") << """
            apply plugin: 'java'
            dependencies { compile project(":api") }
        """
        file("api/build.gradle") << "apply plugin: 'java'"

        when:
        run("impl:build")

        then:
        fixture.assertProjectsConfigured(":", ":impl", ":api")

        when:
        run("impl:build", "--no-rebuild") // impl -> api

        then:
        //api tasks are not executed and api is not configured
        !result.executedTasks.find { it.startsWith ":api" }
        fixture.assertProjectsConfigured(":", ":impl")
    }

    def "respects external task dependencies"() {
        settingsFile << "include 'api', 'impl', 'other'"
        file("build.gradle") << "allprojects { task foo }"
        file("impl/build.gradle") << """
            task bar(dependsOn: ":api:foo")
        """

        when:
        run("impl:bar")

        then:
        fixture.assertProjectsConfigured(":", ":impl", ":api")
        result.assertTasksExecuted(":api:foo", ":impl:bar")
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
}