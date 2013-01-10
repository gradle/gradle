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

/**
 * by Szczepan Faber, created at: 11/21/12
 */
class ConfigurationOnDemandIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("gradle.properties") << "systemProp.org.gradle.configuration.ondemand=true"
        alwaysUsing { it.withArgument('-i') }
    }

    def "works with single-module project"() {
        buildFile << "task foo"
        when:
        run("foo")
        then:
        result.assertProjectsEvaluated(":")
    }

    def "evaluates only project referenced in the task list"() {
        settingsFile << "include 'api', 'impl', 'util', 'util:impl'"
        buildFile << "allprojects { task foo }"

        when:
        run(":foo", ":util:impl:foo")

        then:
        result.assertProjectsEvaluated(":", ":util:impl")
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
        result.assertProjectsEvaluated(":", ":api")

        when:
        run(":impl:build")

        then:
        result.assertProjectsEvaluated(":", ":impl", ":api")

        when:
        run(":util:build")

        then:
        result.assertProjectsEvaluated(":", ":util", ":impl", ":api")
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
        result.assertProjectsEvaluated(':', ':impl', ':api')
    }

    def "name matching execution from root evaluates all projects"() {
        settingsFile << "include 'api', 'impl'"
        buildFile << "task foo"

        when:
        run("foo")

        then:
        result.assertProjectsEvaluated(":", ":api", ":impl")

        when:
        run(":foo")

        then:
        result.assertProjectsEvaluated(":")
    }

    def "name matching execution from subproject evaluates only the subproject recursively"() {
        settingsFile << "include 'api', 'impl:one', 'impl:two', 'impl:two:abc'"
        file("impl/build.gradle") << "task foo"

        when:
        inDirectory("impl")
        run("foo")

        then:
        result.assertProjectsEvaluated(":", ":impl", ":impl:one", ":impl:two", ":impl:two:abc")
    }

    def "may run implicit tasks from root"() {
        settingsFile << "include 'api', 'impl'"

        when:
        run(":tasks")

        then:
        result.assertProjectsEvaluated(":")
    }

    def "may run implicit tasks for subproject"() {
        settingsFile << "include 'api', 'impl'"

        when:
        run(":api:tasks")

        then:
        result.assertProjectsEvaluated(":", ":api")
    }

    def "works with default tasks"() {
        settingsFile << "include 'api', 'impl'"
        file("api/build.gradle") << """
            task foo
            defaultTasks 'foo'
        """

        when:
        inDirectory('api')
        run()

        then:
        result.assertProjectsEvaluated(":", ":api")
        result.assertTasksExecuted(':api:foo')
    }
}