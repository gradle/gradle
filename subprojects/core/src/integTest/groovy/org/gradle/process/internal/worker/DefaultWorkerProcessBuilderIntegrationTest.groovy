/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.process.internal.worker


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

import static org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache.Skip.INVESTIGATE

class DefaultWorkerProcessBuilderIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(skip = INVESTIGATE)
    def "test classpath does not contain nonexistent entries"() {
        given:
        file("src/test/java/ClasspathTest.java") << '''
        import org.junit.Test;

        import static org.junit.Assert.assertTrue;

        public class ClasspathTest {
            @Test
            public void test() {
                String runtimeClasspath = System.getProperty("java.class.path");
                System.out.println(runtimeClasspath);
                assertTrue(runtimeClasspath.contains(System.getProperty("user.home")));
                assertTrue(!runtimeClasspath.contains("Non exist path"));
            }
        }

        '''
        buildFile << """
        plugins {
            id "java-library"
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            testImplementation 'junit:junit:4.13'
        }

        tasks.test {
            doFirst {
                classpath += files(System.getProperty("user.home"),
                        System.getProperty("user.home") + File.separator + "*",
                        System.getProperty("user.home") + File.separator + "Non exist path",
                        System.getProperty("user.home") + File.separator + "Non exist path" + File.separator + "*")
            }
        }
        """

        expect:
        succeeds("test")
    }

}
