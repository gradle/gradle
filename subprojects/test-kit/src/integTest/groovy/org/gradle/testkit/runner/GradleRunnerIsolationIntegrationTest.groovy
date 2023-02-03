/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner


import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.GradleVersion
import org.junit.Assume

class GradleRunnerIsolationIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "configuration in gradle user home is not used by gradle runner builds"() {
        when:
        // Gradle 2.6 leaks native-platform into the test process with a custom GUH
        Assume.assumeTrue(gradleVersion > GradleVersion.version("2.6"))

        def userHome = file("user-home")
        def gradleUserHome = userHome.file(".gradle")

        and:
        gradleUserHome.file("gradle.properties") << 'myProp1=propertiesFile'
        gradleUserHome.file("init.gradle") << 'allprojects { ext.myProp2 = \'initScript\' }'

        and:
        buildScript """
            task check {
                doLast {
                    // Uses testkit dir
                    assert gradle.gradleUserHomeDir == file(new URI("${testKitDir.toURI()}"))
                    assert !project.ext.has('myProp1')
                    assert !project.ext.has('myProp2')
                }
            }
        """

        then:
        (runner('check') as DefaultGradleRunner)
            .withJvmArguments("-Duser.home=$userHome")
            .build()

        when:
        buildScript """
            task check {
                doLast {
                    // uses specified user home dir
                    assert gradle.gradleUserHomeDir == file(new URI("${gradleUserHome.toURI()}"))
                    assert project.ext.myProp1 == "propertiesFile"
                    assert project.ext.myProp2 == "initScript"
                }
            }
        """

        then:
        runner('check', "-g", gradleUserHome.absolutePath).build()
    }

}
