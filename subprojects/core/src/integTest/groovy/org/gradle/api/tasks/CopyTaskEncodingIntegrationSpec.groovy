/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import spock.lang.Issue

@DoesNotSupportNonAsciiPaths(reason = "Uses non-Unicode default charset")
class CopyTaskEncodingIntegrationSpec extends AbstractIntegrationSpec {

    @Issue("https://issues.gradle.org/browse/GRADLE-2181")
    def "can copy files with unicode characters in name with non-unicode platform encoding"() {
        given:
        def nonAsciiFileName = "القيادة والسيطرة - الإدارة.lnk"

        buildFile << """
            task copyFiles {
                doLast {
                    copy {
                        from 'res'
                        into 'build/resources'
                    }
                }
            }
        """

        file("res", nonAsciiFileName) << "foo"

        when:
        executer.withDefaultCharacterEncoding("ISO-8859-1").withTasks("copyFiles")
        executer.run()

        then:
        file("build/resources", nonAsciiFileName).exists()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2181")
    def "can copy files with unicode characters in name with default platform encoding"() {
        given:
        def nonAsciiFileName = "القيادة والسيطرة - الإدارة.lnk"

        buildFile << """
            task copyFiles {
                doLast {
                    copy {
                        from 'res'
                        into 'build/resources'
                    }
                }
            }
        """

        file("res", nonAsciiFileName) << "foo"

        when:
        executer.withTasks("copyFiles").run()

        then:
        file("build/resources", nonAsciiFileName).exists()
    }
}
