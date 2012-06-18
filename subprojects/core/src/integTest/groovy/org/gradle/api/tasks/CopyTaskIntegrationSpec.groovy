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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import spock.lang.Issue
import spock.lang.Ignore

class CopyTaskIntegrationSpec extends AbstractIntegrationSpec {

    @Ignore
    @Issue("http://issues.gradle.org/browse/GRADLE-2181")
    // Note, once this is passing it can be rolled into the one below as a parameterized test
    def "can copy files with unicode characters in name with non-unicode platform encoding"() {
        given:
        def weirdFileName = "القيادة والسيطرة - الإدارة.lnk"

        buildFile << """
            task copyFiles << {
                copy {
                    from 'res'
                    into 'build/resources'
                }
            }
        """

        file("res", weirdFileName) << "foo"

        when:
        executer.withDefaultCharacterEncoding("ISO-8859-1").withTasks("copyFiles")
        onWinOrMacOS() ? executer.run() : executer.runWithFailure()

        then:
        file("build/resources", weirdFileName).exists()
    }


    @Issue("http://issues.gradle.org/browse/GRADLE-2181")
    def "can copy files with unicode characters in name with unicode platform encoding"() {
        given:
        def weirdFileName = "القيادة والسيطرة - الإدارة.lnk"

        buildFile << """
            task copyFiles << {
                copy {
                    from 'res'
                    into 'build/resources'
                }
            }
        """

        file("res", weirdFileName) << "foo"

        when:
        executer.withDefaultCharacterEncoding("UTF-8").withTasks("copyFiles").run()

        then:
        file("build/resources", weirdFileName).exists()
    }

    private boolean onWinOrMacOS() {
        OperatingSystem.current().isWindows() || OperatingSystem.current().isMacOsX()
    }

}
