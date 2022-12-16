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

class ConfigurationCacheFlowScopeIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def 'project plugin can receive build result'() {
        given:
        buildFile '''
            import org.gradle.api.flow.*

            class LavaLampPlugin implements Plugin<Project> {

                final FlowScope flowScope
                final FlowProviders flowProviders

                @Inject
                LavaLampPlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }

                void apply(Project target) {
                    flowScope.always(SetLavaLampColor) {
                        parameters.color = flowProviders.requestedTasksResult.map {
                            it.failure.present ? 'red' : 'green'
                        }
                    }
                }
            }

            class SetLavaLampColor implements FlowAction<Parameters> {

                interface Parameters extends FlowParameters {
                    @Input Property<String> getColor()
                }

                void execute(Parameters parameters) {
                    println "(${parameters.color.get()})"
                }
            }

            apply type: LavaLampPlugin
        '''
        def configCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'help'

        then:
        configCache.assertStateStored(true)
        outputContains '(green)'

        when:
        configurationCacheRun 'help'

        then:
        configCache.assertStateLoaded()
        outputContains '(green)'

        when:
        buildFile '''
            tasks.register('fail') {
                doLast { assert false }
            }
        '''
        configurationCacheFails 'fail'

        then:
        outputContains '(red)'
        configCache.assertStateStored()

        when:
        configurationCacheFails 'fail'

        then:
        outputContains '(red)'
        configCache.assertStateLoaded()
    }
}
