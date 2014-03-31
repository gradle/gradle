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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

@Requires(TestPrecondition.JDK5)
@Unroll
class IvyPublishSftpIncompatibleJavaVersionIntegrationTest extends AbstractIntegrationSpec {
    def "cannot publish to a SFTP repository with layout #layout for incompatible Java version"() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.group.name'

            publishing {
                repositories {
                    ivy {
                        url "sftp://localhost:22/repo"
                        credentials {
                            username 'sftp'
                            password 'sftp'
                        }
                        layout "$layout"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        ExecutionFailure failure = fails 'publish'

        then:
        failure.error.contains("The use of SFTP repositories requires Java 6 or later.")

        where:
        layout   | m2Compatible
        'gradle' | false
        'maven'  | true
    }
}
