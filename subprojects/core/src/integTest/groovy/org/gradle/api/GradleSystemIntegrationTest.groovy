/*
 * Copyright 2018 the original author or authors.
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

class GradleSystemIntegrationTest extends AbstractIntegrationSpec {
    def 'can set env variables via GradleSystem and switch env variables between daemons'() {
        given:
        buildFile << '''
            println GradleSystem.getenv('myEnv')
        '''

        when:
        executer.withEnvironmentVars([myEnv: 'myValue1'])
        succeeds('help')

        then:
        outputContains('myValue1')

        when:
        executer.withEnvironmentVars([myEnv: 'myValue2'])
        succeeds('help')

        then:
        outputContains('myValue2')
    }

    def 'forked workers can read the env variables'() {
        given:
        buildFile << """ 
            apply plugin: 'java'
            
            ${jcenterRepository()}

            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """

        file('src/test/java/EnvVariableReadTest.java') << '''
            public class EnvVariableReadTest {
                @org.junit.Test
                public void readEnvVariable() {
                    org.junit.Assert.assertEquals(System.getenv("myEnv"), "myValue");
                }
            }
        '''
        executer.withEnvironmentVars([myEnv: 'myValue'])

        expect:
        succeeds('test')
    }

    def 'forked Exec tasks can read the env variables'() {
        given:
        buildFile << '''
            apply plugin: 'java'

            task run(type: JavaExec) {
                classpath(sourceSets.main.output.classesDirs)
                main = 'Main'
            }
        '''

        file('src/main/java/Main.java') << '''
            public class Main {
                public static void main(String[] args) {
                    if(!"myValue".equals(System.getenv("myEnv"))) {
                        throw new IllegalStateException();
                    }
                }
            }
        '''
        executer.withEnvironmentVars([myEnv: 'myValue'])

        expect:
        succeeds 'run'
    }
}
