/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

class ConfigurationCacheKotlinScriptReuseIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = 'non-strict ClassLoader scope')
    @Issue('https://github.com/gradle/gradle/issues/32039')
    def 'compiled Kotlin script with non-strict ClassLoaderScope parent can be reused in same build in the presence of version catalog'() {
        given: 'a version catalog'
        file('gradle/libs.versions.toml').text = '''
            [versions]
            # Deleting this line fixes the issue
            test = "1"

            [libraries]
        '''.stripIndent()

        and: 'two projects with the exact same script'
        settingsFile '''
            include("foo")
            include("bar")
            gradle.rootProject {
                // induces non-strict ClassLoaderScope in the hierarchy
                // since this callback runs too early
                buildscript.classLoader
            }
        '''

        for (projectDir in ['foo', 'bar']) {
            kotlinFile "$projectDir/build.gradle.kts", '''
                class StaticScriptData {
                    companion object {
                        val count = java.util.concurrent.atomic.AtomicInteger(1)
                    }
                }
                tasks.register("ok") {
                    doLast {
                        println("count = " + StaticScriptData.count.getAndIncrement())
                    }
                }
            '''
        }

        when:
        executer.withEagerClassLoaderCreationCheckDisabled()

        and:
        configurationCacheRun 'ok'

        then:
        outputContains 'count = 1'
        outputContains 'count = 2'
    }
}
