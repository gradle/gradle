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

package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class EclipseCustomBuildSourceOutputTest extends AbstractEclipseIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id "java"
                id "eclipse"
            }
        """
        file("src/main/java").mkdirs()
        file("src/main/resources").mkdirs()
        file("src/test/java").mkdirs()
        file("src/test/resources").mkdirs()
    }

    @ToBeFixedForConfigurationCache
    def "can configure src output with 'baseSourceOutputDir'"() {
        setup:
        buildFile << """
            eclipse {
                classpath {
                    baseSourceOutputDir = file('custom-output')
                }
            }
        """

        when:
        run 'eclipse'

        then:
        classpath.sourceDirs.size() == 4
        classpath.sourceDirs[0].output == 'custom-output/main'
        classpath.sourceDirs[1].output == 'custom-output/main'
        classpath.sourceDirs[2].output == 'custom-output/test'
        classpath.sourceDirs[3].output == 'custom-output/test'
    }

    @ToBeFixedForConfigurationCache
    def "can configure src output with default value"() {
        when:
        run 'eclipse'

        then:
        classpath.sourceDirs.size() == 4
        classpath.sourceDirs[0].output == 'bin/main'
        classpath.sourceDirs[1].output == 'bin/main'
        classpath.sourceDirs[2].output == 'bin/test'
        classpath.sourceDirs[3].output == 'bin/test'
    }
}
