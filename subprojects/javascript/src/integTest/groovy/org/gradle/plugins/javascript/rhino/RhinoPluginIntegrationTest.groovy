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

package org.gradle.plugins.javascript.rhino

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.WellBehavedPluginTest

class RhinoPluginIntegrationTest extends WellBehavedPluginTest {

    def setup() {
        applyPlugin()

        buildFile << """
            ${mavenCentralRepository()}
        """
        executer.expectDocumentedDeprecationWarning("The org.gradle.rhino plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#deprecated_plugins")
        executer.expectDocumentedDeprecationWarning("The org.gradle.javascript-base plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#deprecated_plugins")
    }


    def "can use default rhino dependency"() {
        when:
        buildFile << """
            task resolve(type: Copy) {
                from javaScript.rhino.classpath
                into "deps"
            }
        """

        then:
        succeeds("resolve")

        and:
        file("deps/rhino-${RhinoExtension.DEFAULT_RHINO_DEPENDENCY_VERSION}.jar").exists()
    }

    @ToBeFixedForInstantExecution
    def "can run rhino exec task"() {
        given:
        file("some.js") << """
            print("rhino js-version: " + version())
            print("rhino arg: " + arguments[0])
        """

        buildFile << """
            task rhino(type: ${RhinoShellExec.name}) {
                rhinoOptions "-version", "160"
                script "some.js"
                scriptArgs "foo"
            }
        """

        when:
        run "rhino"

        then:
        output.contains "rhino js-version: 160"
        output.contains "rhino arg: foo"
    }

    def "compile failure fails task"() {
        given:
        file("some.js") << " ' "

        buildFile << """
            task rhino(type: ${RhinoShellExec.name}) {
                script "some.js"
            }
        """

        expect:
        fails "rhino"
    }

    @ToBeFixedForInstantExecution
    def "can use older rhino version"() {
        given:
        buildFile << """
            dependencies {
                it.${RhinoExtension.CLASSPATH_CONFIGURATION_NAME} "rhino:js:1.6R6"
            }

            task rhino(type: ${RhinoShellExec.name}) {
                rhinoOptions "-e", "print('rhinoClasspath: ' + environment['java.class.path'])"
            }
        """

        when:
        run "rhino"

        then:
        output.readLines().any { it ==~ /rhinoClasspath:.+js-1.6R6.jar/ }
    }

}
