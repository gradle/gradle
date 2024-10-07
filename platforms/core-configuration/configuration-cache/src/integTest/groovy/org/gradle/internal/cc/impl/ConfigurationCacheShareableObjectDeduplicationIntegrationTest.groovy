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
import org.gradle.internal.ShareableObject

class ConfigurationCacheShareableObjectDeduplicationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "shareable objects are shared across projects"() {
        given:
        createDirs 'foo', 'bar'
        settingsFile """
            include 'foo', 'bar'

            abstract class SharingCheckerService implements ${BuildService.name}<${BuildServiceParameters.name}.None>{

                private Object reference = null

                synchronized def check(Object ref) {
                    if (reference === null) {
                        reference = ref
                        println "Instance has been collected"
                    } else {
                        assert reference === ref
                        println "Instances have been shared"
                    }
                }
            }

            class MySharedObject implements ${ShareableObject.name} {
            }

            abstract class SharingCheckerTask extends DefaultTask {
                @Input abstract Property<Object> getObject()
                @ServiceReference('sharingChecker') abstract Property<SharingCheckerService> getService()
                @TaskAction def check() {
                    service.get().check(object.get())
                }
            }

            def service = gradle.sharedServices.registerIfAbsent('sharingChecker', SharingCheckerService) {}

            class Singleton {
                // using a static field to fool isolation from IsolatedAction
                static toBeShared = new MySharedObject()
            }

            gradle.lifecycle.beforeProject { project ->
                println("Registering task against \$project")
                tasks.register('check', SharingCheckerTask) {
                    println("Reference: \${Singleton.toBeShared} from \$project")
                    object = Singleton.toBeShared
                }
            }
        """

        when:
        configurationCacheRun 'check'

        then:
        output.count('Instances have been shared') == 2
    }
}
