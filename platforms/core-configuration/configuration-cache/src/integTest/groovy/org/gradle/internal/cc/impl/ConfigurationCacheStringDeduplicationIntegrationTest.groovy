/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

class ConfigurationCacheStringDeduplicationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "strings are deduplicated across projects"() {
        given:
        createDirs 'foo', 'bar'
        settingsFile """
            include 'foo', 'bar'

            abstract class StringDedupCheckerService implements ${BuildService.name}<${BuildServiceParameters.name}.None>{

                private String string = null

                synchronized def check(String s) {
                    if (string === null) {
                        string = s
                    } else {
                        assert string === s
                        println 'Strings have been deduplicated'
                    }
                }
            }

            abstract class StringTask extends DefaultTask {
                @Input abstract Property<String> getString()
                @ServiceReference('stringChecker') abstract Property<StringDedupCheckerService> getService()
                @TaskAction def check() {
                    service.get().check(string.get())
                }
            }

            def service = gradle.sharedServices.registerIfAbsent('stringChecker', StringDedupCheckerService) {}
            gradle.lifecycle.beforeProject {
                tasks.register('check', StringTask) {
                    string = ['it', 'will', 'be', 'deduplicated'].join(' ')
                }
            }
        """

        when:
        configurationCacheRun 'check'

        then:
        output.count('Strings have been deduplicated') == 2
    }
}
