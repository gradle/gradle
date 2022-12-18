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

import org.gradle.test.fixtures.file.TestFile

class ConfigurationCacheFlowScopeIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def '#target plugin with #parameter can react to task execution result'() {
        given:
        def configCache = newConfigurationCacheFixture()

        and:
        withLavaLampPluginFor target, parameter

        when: 'task runs successfully'
        configurationCacheRun 'help'

        then: 'flow action reacts to build result'
        configCache.assertStateStored(true)
        outputContains '(green)'

        when: 'task from cache runs successfully'
        configurationCacheRun 'help'

        then: 'flow action reacts to build result'
        configCache.assertStateLoaded()
        outputContains '(green)'

        when: 'task fails'
        buildFile '''
            tasks.register('fail') {
                doLast { assert false }
            }
        '''
        configurationCacheFails 'fail'

        then: 'flow action reacts to build failure'
        outputContains '(red)'
        configCache.assertStateStored()

        when: 'task from cache fails'
        configurationCacheFails 'fail'

        then: 'flow action reacts to build failure'
        outputContains '(red)'
        configCache.assertStateLoaded()

        where:
        [target, parameter] << [ScriptTarget.values(), ParameterKind.values()].combinations()
    }

    def '#target plugin with #parameter can react to configuration failure'() {
        given:
        withLavaLampPluginFor target, parameter

        and: 'it fails at configuration time'
        buildFile '''
            assert false
        '''

        when:
        configurationCacheFails 'help'

        then: 'flow action reacts to build failure'
        outputContains '(red)'

        where:
        [target, parameter] << [ScriptTarget.values(), ParameterKind.values()].combinations()
    }

    void withLavaLampPluginFor(ScriptTarget target, ParameterKind parameter) {
        switch (parameter) {
            case ParameterKind.SIMPLE: {
                withSimpleLavaLampPluginFor target
                break
            }
            case ParameterKind.SERVICE_REFERENCE: {
                withLavaLampServicePluginFor target, false
                break
            }
            case ParameterKind.NAMED_SERVICE_REFERENCE: {
                withLavaLampServicePluginFor target, true
                break
            }
        }
    }

    enum ScriptTarget {
        SETTINGS,
        PROJECT;

        String getFileName() {
            this == SETTINGS ? 'settings.gradle' : 'build.gradle'
        }

        String getTargetType() {
            toString().capitalize()
        }

        @Override
        String toString() {
            name().toLowerCase()
        }
    }

    enum ParameterKind {
        SIMPLE,
        SERVICE_REFERENCE,
        NAMED_SERVICE_REFERENCE;

        @Override
        String toString() {
            "${name().toLowerCase().replace('_', ' ')} parameter"
        }
    }

    private withSimpleLavaLampPluginFor(ScriptTarget target) {
        def targetType = target.targetType
        scriptFileFor(target) << """
            import org.gradle.api.flow.*

            class LavaLampPlugin implements Plugin<$targetType> {

                final FlowScope flowScope
                final FlowProviders flowProviders

                @Inject
                LavaLampPlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }

                void apply($targetType target) {
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
                    println "(${'$'}{parameters.color.get()})"
                }
            }

            apply type: LavaLampPlugin
        """
    }

    private withLavaLampServicePluginFor(ScriptTarget target, Boolean namedAnnotation) {
        def targetType = target.targetType
        scriptFileFor(target) << """
            import org.gradle.api.flow.*
            import org.gradle.api.services.*

            class BuildServicePlugin implements Plugin<$targetType> {

                final FlowScope flowScope
                final FlowProviders flowProviders

                @Inject
                BuildServicePlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }

                void apply($targetType target) {
                    def lamp = target.gradle.sharedServices.registerIfAbsent('lamp', LavaLamp) {}
                    flowScope.always(SetLavaLampColor) {
                        ${namedAnnotation ? '' : 'parameters.lamp = lamp'}
                        parameters.color = flowProviders.requestedTasksResult.map {
                            it.failure.present ? 'red' : 'green'
                        }
                    }
                }
            }

            class SetLavaLampColor implements FlowAction<Parameters> {

                interface Parameters extends FlowParameters {
                    @ServiceReference${namedAnnotation ? '("lamp")' : ''} Property<LavaLamp> getLamp()
                    @Input Property<String> getColor()
                }

                void execute(Parameters parameters) {
                    parameters.with {
                        lamp.get().setColor(color.get())
                    }
                }
            }

            abstract class LavaLamp implements BuildService<BuildServiceParameters.None> {
                void setColor(String color) {
                    println('(' + color + ')')
                }
            }

            apply type: BuildServicePlugin
        """
    }

    private TestFile scriptFileFor(ScriptTarget target) {
        file(target.fileName)
    }
}
