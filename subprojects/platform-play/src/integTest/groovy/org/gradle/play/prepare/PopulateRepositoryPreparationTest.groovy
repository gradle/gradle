/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.play.prepare

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.play.integtest.fixtures.PlayMultiVersionIntegrationTest
import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.app.PlayAppWithDependencies
import org.gradle.play.integtest.fixtures.app.PlayCompositeBuild
import org.gradle.play.integtest.fixtures.app.WithFailingTestsApp

class PopulateRepositoryPreparationTest extends PlayMultiVersionIntegrationTest {

    @ToBeFixedForConfigurationCache(because = "uses configurations at execution time")
    void "populates repository"() {
        playApp.writeSources(testDirectory)
        buildFile << """

allprojects {
    model {
        components {
            play {
                targetPlatform "play-${MultiVersionIntegrationSpec.version}"
            }
        }
    }

    task resolveAll {
        doFirst {
           configurations.all {
               if (it.canBeResolved) {
                  println "Preemptively download all files for configuration \${it.name}"
                  println it.resolve()*.name
               }
           }
        }
    }
}
"""

        expect:
        run 'resolveAll'

        where:
        playApp << [new BasicPlayApp(versionNumber), new AdvancedPlayApp(versionNumber), new PlayAppWithDependencies(versionNumber), new PlayCompositeBuild(versionNumber), new WithFailingTestsApp(versionNumber)]
    }
}
