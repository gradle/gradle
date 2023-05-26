/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

class ConfigurationCacheDependencyResolutionFailuresIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def 'nested dependency resolution failures are surfaced to the top'() {
        given:
        def emptyRepo = createDir('empty')
        buildFile '''

            configurations {
                implementation
            }

            dependencies {
                implementation 'non:existent:1.0'
            }

            repositories {
                maven { url = uri('${emptyRepo.uri}') }
            }

            class Bean {
                @InputFiles FileCollection files
            }

            abstract class Test extends DefaultTask {
                @Nested abstract Property<Bean> getBean()
                @TaskAction def test() { assert false }
            }

            tasks.register('test', Test) {
                bean = new Bean().tap {
                    files = configurations.implementation
                }
            }
        '''

        when:
        configurationCacheFails 'test'

        then:
        failure.assertHasFailure("Configuration cache state could not be cached: field `files` of `Bean` bean found in field `__bean__` of task `:test` of type `Test`: error writing value of type 'org.gradle.api.internal.artifacts.configurations.DefaultLegacyConfiguration'") {
            it.assertHasFirstCause("Could not resolve all files for configuration ':implementation'.")
        }
    }
}
