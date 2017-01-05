/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.api.internal.cache.GeneratedGradleJarCache
import org.gradle.util.GradleVersion

class BuildSrcContinuousIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    def setup() {
        def generatedGradleJarCacheDir = new File(executer.gradleUserHomeDir, "caches/${GradleVersion.current().version}/$GeneratedGradleJarCache.CACHE_KEY")

        // Increase build timeout if generated Gradle JARs do not exist yet (e.g. when bumping up the Gradle version)
        if (!generatedGradleJarCacheDir.isDirectory()) {
            buildTimeout = 60
        }
    }

    def "can build and reload a project with buildSrc"() {
        when:
        file("buildSrc/src/main/groovy/Thing.groovy") << """
            class Thing {
              public static final String VALUE = "original"
            }
        """

        buildScript """
            task a {
              inputs.files "a"
              doLast {
                println "value: " + Thing.VALUE
              }
            }
        """

        then:
        succeeds("a")
        output.contains "value: original"

        when:
        file("buildSrc/src/main/groovy/Thing.groovy").text = """
            class Thing {
              public static final String VALUE = "changed"
            }
        """

        then:
        noBuildTriggered()

        when:
        file("a") << "added"

        then:
        succeeds()
        output.contains "value: changed"
    }

}
