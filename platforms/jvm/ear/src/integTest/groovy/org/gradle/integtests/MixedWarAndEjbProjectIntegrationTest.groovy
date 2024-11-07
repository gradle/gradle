/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MixedWarAndEjbProjectIntegrationTest extends AbstractIntegrationSpec {
    def "project can use compiled classes from an EAR project"() {
        given:
        file("settings.gradle") << 'include "a", "b"'

        and:
        buildFile << """
project(":a") {
    apply plugin: 'ear'
    apply plugin: 'java'
}
project(":b") {
    apply plugin: 'war'
    dependencies {
        implementation project(":a")
    }
    compileJava.doFirst {
        assert classpath.collect { it.name } == ['a.jar']
    }
}
"""

        and:
        file("a/src/main/java/org/gradle/test/Person.java") << """
package org.gradle.test;
interface Person { }
"""

        and:
        file("b/src/main/java/org/gradle/test/PersonImpl.java") << """
package org.gradle.test;
class PersonImpl implements Person { }
"""

        expect:
        succeeds "assemble"
    }

    def "assemble builds the JAR, WAR, and EAR by default"() {
        given:
        file("settings.gradle") << "rootProject.name = 'root'"

        and:
        buildFile << """
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'ear'
"""

        when:
        run "assemble"

        then:
        file("build/libs/root.jar").exists()
        file("build/libs/root.war").exists()
        file("build/libs/root.ear").exists()
    }
}
