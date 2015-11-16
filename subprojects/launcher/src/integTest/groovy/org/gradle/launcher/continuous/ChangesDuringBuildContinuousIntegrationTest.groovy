/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous
// Continuous build will trigger a rebuild when an input file is changed during build execution
class ChangesDuringBuildContinuousIntegrationTest extends Java7RequiringContinuousIntegrationTest {
    def setup() {
        buildFile << """
        |apply plugin: 'java'
        |""".stripMargin()
    }

    def "should trigger rebuild when java source file is changed during build execution"() {
        given:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        buildFile << """
gradle.taskGraph.afterTask { Task task ->
    if(task.path == ':classes') {
       file("src/main/java/Thing.java").text = "class Thing { private static final boolean CHANGED=true; }"
    }
}
"""
        then:
        succeeds("build")

        when:
        sendEOT()

        then:
        cancelsAndExits()

        when:
        def classloader = new URLClassLoader([file("build/classes/main").toURI().toURL()] as URL[])

        then:
        assert classloader.loadClass('Thing').getDeclaredField("CHANGED") != null
    }
}
