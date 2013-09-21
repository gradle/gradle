/*
 * Copyright 2013 the original author or authors.
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
import spock.lang.Ignore

public class BuildEventsErrorIntegrationTest extends AbstractIntegrationSpec {

    def "produces reasonable error message when taskGraph.whenReady action fails"() {
        buildFile << """
    gradle.taskGraph.whenReady {
        throw new RuntimeException('broken closure')
    }
    task a
"""

        when:
        fails()

        then:
        failure.assertHasDescription("broken closure")
                .assertHasNoCause()
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(3);
    }

    def "produces reasonable error message when task dependency closure throws exception"() {
        buildFile << """
    task a
    a.dependsOn {
        throw new RuntimeException('broken')
    }
"""
        when:
        fails "a"

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a'.")
                .assertHasCause('broken')
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(4)
    }

    def "produces reasonable error when Gradle.allprojects action fails"() {
        def initScript = file("init.gradle") << """
allprojects {
    throw new RuntimeException("broken closure")
}
"""
        when:
        executer.usingInitScript(initScript)
        fails "a"

        then:
        failure.assertHasDescription("broken closure")
                .assertHasNoCause()
                .assertHasFileName("Initialization script '$initScript'")
                .assertHasLineNumber(3);
    }

    @Ignore
    def "produces reasonable error when Gradle.buildFinished action fails"() {
        def initScript = file("init.gradle") << """
rootProject { task a }
buildFinished {
    throw new RuntimeException("broken closure")
}
"""
        when:
        executer.usingInitScript(initScript)
        fails "a"

        then:
        failure.assertHasDescription("broken closure")
                .assertHasNoCause()
                .assertHasFileName("Initialization script '$initScript'")
                .assertHasLineNumber(3);
    }
}
