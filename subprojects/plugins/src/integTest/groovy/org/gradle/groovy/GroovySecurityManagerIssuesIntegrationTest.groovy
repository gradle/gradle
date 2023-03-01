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

package org.gradle.groovy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore
import spock.lang.Issue

class GroovySecurityManagerIssuesIntegrationTest extends AbstractIntegrationSpec {

    @Ignore
    @Issue("GRADLE-2170")
    def "build does not hang when a GroovyTestCase sets a SecurityManager"() {
        given:
        writeJavaTestSource("src/main/groovy")
        writeGroovyTestSource("src/test/groovy")
        file('build.gradle') << """
            apply plugin:'groovy'
            ${mavenCentralRepository()}
            dependencies{
                compile localGroovy()
                testCompile 'junit:junit:4.13'
            }
            """
        executer.withArguments("-i")

        expect:
        succeeds("test")
    }

    def writeJavaTestSource(String srcDir, String clazzName = "JavaClazz") {
        file(srcDir, "org/test/${clazzName}.java") << """
            package org.test;
            public class ${clazzName} { }
            """
    }

    def writeGroovyTestSource(String srcDir) {
        file(srcDir, 'org/test/SecurityTest.groovy') << """
            package org.test
            import java.security.*;

            class SecurityTest extends SecurityTestSupport {
                    public void testSecurityManager() {
                        for (int i = 0; i < 2; i++) {
                            System.out.println("ROUND" + i);
                            setUp();
                            System.out.println("Complete" + i);
                        }
                    }
            }

            class SecurityTestSupport extends GroovyTestCase {
                //Must hold a reference to reproduce the bug
                private SecurityManager securityManager;

                //This usually need to be called more than once to reproduce the bug
                protected void setUp() {
                    securityManager = System.getSecurityManager();
                    System.out.println("-----------Setting new security manager");
                    System.setSecurityManager(new SecurityManager());
                }
            }
            """
    }

}
