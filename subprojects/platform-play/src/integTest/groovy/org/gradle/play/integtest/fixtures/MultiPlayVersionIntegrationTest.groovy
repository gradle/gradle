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

package org.gradle.play.integtest.fixtures
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.test.fixtures.archive.JarTestFixture

@TargetCoverage({PlayCoverage.DEFAULT})
abstract class MultiPlayVersionIntegrationTest extends MultiVersionIntegrationSpec {

    def setup() {
        buildFile <<"""
        ${pluginsBlock}

        model {
            components {
                play {
                    playVersion "${version}"
                }
            }
        }

        repositories{
            jcenter()
            maven{
                name = "typesafe-maven-release"
                url = "https://repo.typesafe.com/typesafe/maven-releases"
            }
        }
"""
    }

    def getPluginsBlock() {
        return """
            plugins {
                id 'play'
            }
        """
    }


    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }

}
