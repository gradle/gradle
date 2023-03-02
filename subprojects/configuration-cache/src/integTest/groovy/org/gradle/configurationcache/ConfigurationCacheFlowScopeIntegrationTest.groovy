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

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.process.ExecOperations
import org.gradle.test.fixtures.file.TestFile

class ConfigurationCacheFlowScopeIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def '#target #injectionStyle with #parameter can react to task execution result'() {
        given:
        def configCache = newConfigurationCacheFixture()

        and:
        withLavaLampPluginFor target, parameter, injectionStyle

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
        [target, parameter, injectionStyle] << [
            ScriptTarget.values(),
            ParameterKind.values(),
            InjectionStyle.values()
        ].combinations()
    }

    def '#target #injectionStyle with #parameter can react to configuration failure'() {
        given:
        withLavaLampPluginFor target, parameter, injectionStyle

        and: 'it fails at configuration time'
        buildFile '''
            assert false
        '''

        when:
        configurationCacheFails 'help'

        then: 'flow action reacts to build failure'
        outputContains '(red)'

        where:
        [target, parameter, injectionStyle] << [
            ScriptTarget.values(),
            ParameterKind.values(),
            InjectionStyle.values()
        ].combinations()
    }

    def '#scriptTarget action can use injectable #simpleServiceTypeName'() {
        given:
        scriptFileFor(scriptTarget) << """
            import org.gradle.api.flow.*

            class FlowActionInjectionPlugin implements Plugin<$targetType> {

                final FlowScope flowScope
                final FlowProviders flowProviders

                @Inject
                FlowActionInjectionPlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }

                void apply($targetType target) {
                    flowScope.always(FlowActionInjection) {
                    }
                }
            }

            class FlowActionInjection implements FlowAction<FlowParameters.None> {

                private final $serviceType.name service

                @Inject
                FlowActionInjection($serviceType.name service) {}

                void execute(FlowParameters.None parameters) {
                    println("(green)")
                }
            }

            apply type: FlowActionInjectionPlugin
        """

        when:
        configurationCacheRun 'help'

        then:
        outputContains '(green)'

        where:
        [scriptTarget, serviceType] << [
            ScriptTarget.values(),
            [ArchiveOperations, FileSystemOperations, ExecOperations]
        ].combinations()
        targetType = scriptTarget.targetType
        simpleServiceTypeName = serviceType.simpleName
    }

    def '#scriptTarget action can use build service registered #beforeOrAfter prior to the service being closed'() {
        given:
        def configCache = newConfigurationCacheFixture()
        scriptFileFor(scriptTarget) << """
            import org.gradle.api.flow.*
            import org.gradle.api.services.*

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
                    @ServiceReference("lamp") Property<LavaLamp> getLamp()
                    Property<String> getColor()
                }

                void execute(Parameters parameters) {
                    parameters.with {
                        lamp.get().setColor(color.get())
                    }
                }
            }

            abstract class LavaLamp implements BuildService<BuildServiceParameters.None>, AutoCloseable {

                private Boolean closed = false

                void setColor(String color) {
                    assert !closed
                    println('(' + color + ')')
                }

                @Override
                void close() {
                    println '(closed)'
                    closed = true
                }
            }

            ${registerServiceBefore ? "gradle.sharedServices.registerIfAbsent('lamp', LavaLamp) {}" : ''}
            apply type: LavaLampPlugin
            ${registerServiceBefore ? '' : "gradle.sharedServices.registerIfAbsent('lamp', LavaLamp) {}"}
        """
        buildFile '''
            task ok
            task fail { doLast { assert false } }
        '''

        when:
        configurationCacheRun 'ok'

        then:
        configCache.assertStateStored()

        and:
        outputContains '(green)'
        outputContains '(closed)'

        when:
        configurationCacheRun 'ok'

        then:
        configCache.assertStateLoaded()

        and:
        outputContains '(green)'
        outputContains '(closed)'

        when:
        configurationCacheFails 'fail'

        then:
        configCache.assertStateStored()

        and:
        outputContains '(red)'
        outputContains '(closed)'

        when:
        configurationCacheFails 'fail'

        then:
        configCache.assertStateLoaded()

        and:
        outputContains '(red)'
        outputContains '(closed)'

        where:
        [scriptTarget, registerServiceBefore] << [
            ScriptTarget.values(),
            [true, false]
        ].combinations()
        targetType = scriptTarget.targetType
        beforeOrAfter = registerServiceBefore ? 'before' : 'after'
    }

    void withLavaLampPluginFor(ScriptTarget target, ParameterKind parameter, InjectionStyle injectionStyle) {
        switch (parameter) {
            case ParameterKind.SIMPLE: {
                withSimpleLavaLampPluginFor target, injectionStyle
                break
            }
            case ParameterKind.SERVICE_REFERENCE: {
                withLavaLampServicePluginFor target, injectionStyle, false
                break
            }
            case ParameterKind.NAMED_SERVICE_REFERENCE: {
                withLavaLampServicePluginFor target, injectionStyle, true
                break
            }
        }
    }

    private withSimpleLavaLampPluginFor(ScriptTarget target, InjectionStyle injectionStyle) {
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

            ${applyPluginUsing(injectionStyle, target, 'LavaLampPlugin')}
        """
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

    enum InjectionStyle {
        PLUGIN,
        NEW_INSTANCE;

        @Override
        String toString() {
            name().toLowerCase().replace('_', ' ')
        }
    }

    private withLavaLampServicePluginFor(ScriptTarget target, InjectionStyle injectionStyle, Boolean namedAnnotation) {
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

            ${applyPluginUsing(injectionStyle, target, 'BuildServicePlugin')}
        """
    }

    String applyPluginUsing(InjectionStyle injectionStyle, ScriptTarget target, String pluginType) {
        switch (injectionStyle) {
            case InjectionStyle.PLUGIN:
                return "apply type: $pluginType"
            case InjectionStyle.NEW_INSTANCE:
                def targetObject = target.targetType.toLowerCase()
                switch (target) {
                    case ScriptTarget.PROJECT:
                        return "objects.newInstance($pluginType).apply($targetObject)"
                    case ScriptTarget.SETTINGS: // settings DSL doesn't expose `objects`
                        return "extensions.create('$pluginType', $pluginType).apply($targetObject)"
                }
        }
    }

    private TestFile scriptFileFor(ScriptTarget target) {
        file(target.fileName)
    }

    def "value source with task result provider cannot be obtained at configuration time"() {
        given:
        buildFile("""
        import org.gradle.api.provider.*

        abstract class ResultSource implements ValueSource<String, Params> {
            interface Params extends ValueSourceParameters {
                Property<RequestedTasksResult> getTasksResult();
            }

            @Override String obtain() {
                return "I'm not using my parameter"
            }
        }

        interface FlowProvidersGetter {
            @Inject FlowProviders getFlowProviders()
        }

        def flowProviders = objects.newInstance(FlowProvidersGetter).flowProviders

        providers.of(ResultSource) {
            parameters.tasksResult = flowProviders.requestedTasksResult
        }.get()

        """)

        expect:
        configurationCacheFails()
        // TODO(mlopatkin) The error message can be improved.
        failureHasCause(~/Could not isolate value ResultSource(.*) of type ResultSource.Params/)
    }
}
