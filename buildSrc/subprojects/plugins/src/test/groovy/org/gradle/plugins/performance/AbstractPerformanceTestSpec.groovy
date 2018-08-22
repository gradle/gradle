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
package org.gradle.plugins.performance

abstract class AbstractPerformanceTestSpec extends AbstractIntegrationTest {
    String buildscriptBlock() {
        """
            buildscript {
                dependencies {
                    ${concatClasspaths('classpath', System.getProperty('test.classpath').split(File.pathSeparator))}
                }
            }
        """
    }

    String concatClasspaths(String conf, def classpaths) {
        return classpaths.collect { "${conf} files('${it.replace('\\', '/')}')" }.join('\n')
    }

    String junitClasspath() {
        List junitClasspath = System.getProperty('test.classpath').split(File.pathSeparator).findAll { it.contains('junit') || it.contains('hamcrest') }
        assert !junitClasspath.empty
        return concatClasspaths('testCompile', junitClasspath)
    }

    String testFile() {
        '''
        public class Test {
            @org.junit.Test
            public void readSystemProperties() {
                printSystemProperty("org.gradle.performance.scenarios");
                printSystemProperty("org.gradle.performance.baselines");
                printSystemProperty("org.gradle.performance.execution.warmups");
                printSystemProperty("org.gradle.performance.execution.runs");
                printSystemProperty("org.gradle.performance.execution.checks");
                printSystemProperty("org.gradle.performance.execution.channel");
                printSystemProperty("org.gradle.performance.flameGraphTargetDir");
                printSystemProperty("org.gradle.performance.db.url");
                printSystemProperty("org.gradle.performance.db.username");
            }

            private void printSystemProperty(String key) {
                System.out.println(key + "=" + System.getProperty(key));
            }
        }
        '''
    }
}
