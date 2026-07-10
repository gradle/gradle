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

import org.gradle.test.fixtures.file.TestFile
import spock.lang.Ignore
import spock.lang.Issue

class ConfigurationCacheClassLoaderValidationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Ignore //not yet implemented
    @Issue('https://github.com/gradle/gradle/issues/28727')
    def "invalidates entry when script classpath file dependency changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def jarFile = file('lib.jar')
        jarWithPrintTask(jarFile, 'Version 1')

        buildFile """
            buildscript {
                dependencies {
                    classpath(files('${jarFile.toURI()}'))
                }
            }

            tasks.register('ok', PrintTask)
        """

        when:
        configurationCacheRun 'ok'

        then:
        output.contains('Version 1')
        configurationCache.assertStateStored()

        when:
        jarWithPrintTask(jarFile, 'Version 42')

        and:
        configurationCacheRun 'ok'

        then:
        output.contains('Version 42')
        configurationCache.assertStateStored()

        when:
        configurationCacheRun 'ok'

        then:
        output.contains('Version 42')
        configurationCache.assertStateLoaded()
    }

    private void jarWithPrintTask(TestFile jarFile, String message) {
        jarWithClasses(
            jarFile,
            PrintTask: """
                import org.gradle.api.*;
                import org.gradle.api.tasks.*;
                public class PrintTask extends DefaultTask {
                   @TaskAction void printValue() {
                        System.out.println("$message");
                    }
                }
            """
        )
    }
}
