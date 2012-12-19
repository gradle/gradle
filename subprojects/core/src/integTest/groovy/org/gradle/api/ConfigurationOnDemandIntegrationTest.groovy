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
import org.gradle.integtests.fixtures.executer.ExecutionResult

/**
 * by Szczepan Faber, created at: 11/21/12
 */
class ConfigurationOnDemandIntegrationTest extends AbstractIntegrationSpec {

    //TODO SF more coverage, possibly new integ test 'mode', develop 'proper' fixture code
    def "projects are evaluated on demand"() {
        settingsFile << "include 'api', 'impl', 'util'"

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

        buildFile << """
gradle.projectsEvaluated {
  println "all evaluated"
}

allprojects {
  task foo
  afterEvaluate {
    println "evaluated " + path
  }
}
"""

        file("api/build.gradle") << """
apply plugin: 'java'
//foo.dependsOn ":util:utility"
"""
        file("impl/build.gradle") << """
apply plugin: 'java'
dependencies {
    compile project(":api")
}
"""
        file("util/build.gradle") << "task utility"

        file("gradle.properties") << "systemProp.org.gradle.configuration.ondemand=true"

        when:
        run(":api:build")

        then:
        assertEvaluated(":", ":api")
        assertNotEvaluated(":util", ":impl")

//        when:
//        run(":api:foo")
//
//        then:
//        assertEvaluated(":", ":api", ":util")
//        assertNotEvaluated(":impl")
//
        when:
        run(":impl:build")

        then:
        assertEvaluated(":", ":api", ":impl")
        assertNotEvaluated(":util")

        when:
        run(":foo")

        then:
        assertEvaluated(":")
        assertNotEvaluated(":api", ":impl", ":util")

        when:
        run("foo")

        then:
        assertEvaluated(":", ":api", ":impl", ":util")

        when:
        inDirectory("api")
        run("build")

        then:
        assertEvaluated(":", ":api")
        assertNotEvaluated(":impl", ":util")

        when:
        inDirectory("impl")
        run("build")

        then:
        assertEvaluated(":", ":api", ":impl")
        assertNotEvaluated(":util")

        when:
        run("impl:dependencies")
        then:
        assertEvaluated(":", ":impl")
        assertNotEvaluated(":util", ":api")
    }

    ExecutionResult run(String ... tasks) {
        super.run(tasks)
    }

    void assertEvaluated(String ... paths) {
        paths.each {
            assert output.contains("evaluated $it")
            assert output.contains("all evaluated")
            //making sure the global projectsEvaluated hook was fired last
            assert output.indexOf("all evaluated") > output.indexOf("evaluated $it")
        }
    }

    void assertNotEvaluated(String ... paths) {
        paths.each {
            assert !output.contains("evaluated $it")
        }
    }
}