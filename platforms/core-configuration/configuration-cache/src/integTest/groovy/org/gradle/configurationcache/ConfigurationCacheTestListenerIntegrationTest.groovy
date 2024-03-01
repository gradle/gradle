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

package org.gradle.configurationcache

import org.gradle.test.fixtures.dsl.GradleDsl
import spock.lang.Issue

class ConfigurationCacheTestListenerIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue('https://github.com/gradle/gradle/issues/26465')
    def 'can register KotlinClosure listener'() {
        given:
        buildKotlinFile """
            plugins {
                id("java-library")
            }
            testing {
                suites {
                    named<JvmTestSuite>("test") {
                        useJUnitJupiter()
                    }
                }
            }
            tasks.test {
                onOutput(
                    KotlinClosure2<TestDescriptor, TestOutputEvent, Any>({ descriptor, event ->
                        println("onOutput:" + descriptor.displayName + ":" + event.message)
                    })
                )
            }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """

        and:
        file('src/test/java/my/TestClass.java') << '''
            public class TestClass {
                @org.junit.jupiter.api.Test
                void testMethod() {
                    System.out.println("42");
                }
            }
        '''

        when:
        configurationCacheRun 'test'

        then:
        outputContains 'onOutput:testMethod():42'
    }
}
