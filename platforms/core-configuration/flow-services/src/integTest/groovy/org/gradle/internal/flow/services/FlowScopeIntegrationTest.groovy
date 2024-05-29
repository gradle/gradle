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

package org.gradle.internal.flow.services

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.process.ExecOperations
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class FlowScopeIntegrationTest extends AbstractIntegrationSpec {

    @Requires(
        value = IntegTestPreconditions.IsConfigCached,
        reason = "Isolation provided by Configuration Cache serialization"
    )
    def 'flow actions are isolated from each other'() {
        given: 'flow actions that share a bean'
        buildFile '''
            import org.gradle.api.flow.*
            import org.gradle.api.services.*
            import java.util.concurrent.atomic.AtomicInteger

            class FlowActionPlugin implements Plugin<Project> {
                final FlowScope flowScope
                final FlowProviders flowProviders
                @Inject FlowActionPlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }
                void apply(Project target) {
                    def sharedBean = new Bean()
                    def sharedBeanProvider = flowProviders.buildWorkResult.map { sharedBean }
                    2.times {
                        flowScope.always(IncrementAndPrint) {
                            parameters {
                                bean = sharedBeanProvider
                            }
                        }
                    }
                }
            }

            class Bean {
                AtomicInteger value = new AtomicInteger(41)
            }

            class IncrementAndPrint implements FlowAction<Parameters> {
                interface Parameters extends FlowParameters {
                    @Input Property<Bean> getBean()
                }
                void execute(Parameters parameters) {
                    parameters.with {
                        println("Bean.value = " + bean.get().value.incrementAndGet())
                    }
                }
            }

            apply type: FlowActionPlugin
        '''

        when:
        succeeds 'help'

        then: 'shared bean should have been isolated'
        output.count('Bean.value = 42') == 2
        outputDoesNotContain 'Bean.value = 43'
    }

    def 'flow actions cannot depend on tasks'() {
        given:
        buildFile '''
            import org.gradle.api.flow.*
            import org.gradle.api.services.*
            import org.gradle.api.tasks.*

            class FlowActionPlugin implements Plugin<Project> {
                final FlowScope flowScope
                final FlowProviders flowProviders
                @Inject FlowActionPlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }
                void apply(Project target) {
                    def producer = target.tasks.register('producer', Producer) {
                        outputFile = target.layout.buildDirectory.file('out')
                    }
                    flowScope.always(PrintAction) {
                        parameters.text = producer.flatMap { it.outputFile }.map { it.asFile.text }
                    }
                }
            }

            abstract class Producer extends DefaultTask {
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction def produce() {
                    outputFile.get().asFile << "42"
                }
            }

            class PrintAction implements FlowAction<Parameters> {
                interface Parameters extends FlowParameters {
                    @Input Property<String> getText()
                }
                void execute(Parameters parameters) {
                    println(parameters.text.get())
                }
            }

            apply type: FlowActionPlugin
        '''

        when:
        fails 'producer'

        then:
        failureCauseContains "Property 'text' cannot carry a dependency on task ':producer' as these are not yet supported."
    }

    def '#scriptTarget action can use injectable #simpleServiceTypeName'() {
        given:
        groovyFile scriptTarget.fileName, """
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
        succeeds 'help'

        then:
        outputContains '(green)'

        where:
        [scriptTarget_, serviceType_] << [
            ScriptTarget.values(),
            [ArchiveOperations, FileSystemOperations, ExecOperations]
        ].combinations()
        scriptTarget = scriptTarget_ as ScriptTarget
        serviceType = serviceType_ as Class<?>
        targetType = scriptTarget.targetType
        simpleServiceTypeName = serviceType.simpleName
    }

    def "value source with build work result provider cannot be obtained at configuration time"() {
        given:
        buildFile '''
            import org.gradle.api.provider.*

            abstract class ResultSource implements ValueSource<String, Params> {
                interface Params extends ValueSourceParameters {
                    Property<BuildWorkResult> getWorkResult();
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
                parameters.workResult = flowProviders.buildWorkResult
            }.get()

        '''

        expect:
        fails()

        and:
        // TODO(mlopatkin) The error message can be improved.
        failureHasCause(~/Could not isolate value ResultSource(.*) of type ResultSource.Params/)
    }

    def "task cannot depend on buildWorkResult, fails with clear message and problem is reported"() {
        given:
        buildFile '''
            abstract class Fails extends DefaultTask {

                @Input abstract Property<String> getColor()

                @TaskAction void wontRun() {
                    assert false
                }
            }

            abstract class FailsPlugin implements Plugin<Project> {

                @Inject abstract FlowProviders getFlowProviders()

                void apply(Project target) {
                    target.tasks.register('fails', Fails)  {
                        color = flowProviders.buildWorkResult.map {
                            it.failure.present ? 'red' : 'green'
                        }
                    }
                }
            }

            apply type: FailsPlugin
        '''

        when:
        fails 'fails'

        then:
        failureHasCause "Failed to calculate the value of task ':fails' property 'color'."
        failureHasCause "Cannot access the value of 'BuildWorkResult' before it becomes available!"
        if (GradleContextualExecuter.configCache) {
            failureDescriptionStartsWith "Configuration cache problems found in this build"
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
}
