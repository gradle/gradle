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
    }

    //TODO SF more coverage, possibly new integ test 'mode'
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

        buildFile << "task foo"

        file("api/build.gradle") << "apply plugin: 'java'"

        file("impl/build.gradle") << """
apply plugin: 'java'
dependencies {
    compile project(":api")
}
"""
        file("util/build.gradle") << "task utility"

        when:
        run(":api:build", "-i")

        then:
        result.assertProjectsEvaluated(":", ":api")

        when:
        run(":impl:build", "-i")

        then:
        result.assertProjectsEvaluated(":", ":impl", ":api")

        when:
        run(":projects", "-i")

        then:
        result.assertProjectsEvaluated(":")

        when:
        run("foo", "-i")

        then:
        result.assertProjectsEvaluated(":", ":api", ":impl", ":util")

        when:
        inDirectory("api")
        run("build", "-i")

        then:
        result.assertProjectsEvaluated(":", ":api")

        when:
        inDirectory("impl")
        run("build", "-i")

        then:
        result.assertProjectsEvaluated(":", ":impl", ":api")

        when:
        run("impl:dependencies", "-i")
        then:
        result.assertProjectsEvaluated(":", ":impl")
    }
}