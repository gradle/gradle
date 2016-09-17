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
package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class BasePluginIntegrationTest extends AbstractIntegrationSpec {

    @Requires(TestPrecondition.MANDATORY_FILE_LOCKING)
    def "clean failure message indicates file"() {
        given:
        executer.requireGradleDistribution()
        buildFile << """
            apply plugin: 'base'
        """

        and:
        def channel = new RandomAccessFile(file("build/newFile").createFile(), "rw").channel
        def lock = channel.lock()

        when:
        fails "clean"

        then:
        failure.assertHasCause("Unable to delete file")

        cleanup:
        lock?.release()
        channel?.close()
    }

    def "cannot define 'build' and 'check' tasks when applying plugin"() {
        buildFile << """
            apply plugin: 'base'

            task build {
                dependsOn 'check'
                doLast {
                    println "CUSTOM BUILD"
                }
            }

            task check {
                doLast {
                    println "CUSTOM CHECK"
                }
            }
"""
        when:
        fails "build"

        then:
        failure.assertHasCause "Declaring custom 'build' task when using the standard Gradle lifecycle plugins is not allowed"
    }


    def "can define 'default' and 'archives' configurations prior to applying plugin"() {
        buildFile << """
            configurations {
                "default"
                archives
            }
            apply plugin: 'base'
"""
        expect:
        succeeds "tasks"
    }
}
