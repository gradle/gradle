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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class DifferentJnaVersionInPluginIntegrationSpec extends AbstractIntegrationSpec {
    @Requires(UnitTestPreconditions.NotMacOsM1)
    def 'can build a plugin with a different jna version'() {
        given:
        buildScript """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}
            dependencies {
                implementation gradleApi()
                implementation 'net.java.dev.jna:jna:4.1.0'
                testImplementation 'junit:junit:4.13'
            }
        """.stripIndent()

        file('src/test/java/JnaDoesWork.java') << """
            import com.sun.jna.Native;
            import org.junit.Test;

            public class JnaDoesWork {
                @Test
                public void callMethod() throws Exception {
                    Native.isProtected();
                }
            }
        """.stripIndent()
        executer.requireOwnGradleUserHomeDir()  // Have the gradle-api-jar regenerated

        expect:
        succeeds 'test'

    }
}
