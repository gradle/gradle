/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.JDK5)
class IvySftpRepoResolveIncompatibleJavaVersionIntegrationTest extends AbstractIntegrationSpec {
    void "cannot resolve dependencies from a SFTP Ivy repository for incompatible Java version"() {
        given:
        buildFile << """
            repositories {
                ivy {
                    url "sftp://localhost:22/repo"
                    credentials {
                        username 'sftp'
                        password 'sftp'
                    }
                }
            }
            configurations { compile }
            dependencies { compile 'org.group.name:projectA:1.2' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:
        ExecutionFailure failure = fails 'retrieve'

        then:
        failure.assertHasCause("The use of SFTP repositories requires Java 6 or later.")
    }
}
