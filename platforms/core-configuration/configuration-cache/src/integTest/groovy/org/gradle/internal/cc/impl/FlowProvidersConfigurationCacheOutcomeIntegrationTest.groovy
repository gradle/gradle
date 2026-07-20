/*
 * Copyright 2026 the original author or authors.
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

class FlowProvidersConfigurationCacheOutcomeIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "flow action observes NOT_ENABLED when the configuration cache is not used"() {
        given:
        withOutcomePrintingPluginIn settingsFile

        when:
        run DISABLE_CLI_OPT, 'help'

        then:
        outputContains 'CC outcome: NOT_ENABLED'
    }

    def "flow action observes STORED on a cache miss and REUSED on a cache hit"() {
        given:
        def configCache = newConfigurationCacheFixture()
        withOutcomePrintingPluginIn settingsFile

        when: 'first run stores the entry'
        configurationCacheRun 'help'

        then: 'the store-then-reload run reports STORED, not REUSED'
        configCache.assertStateStored()
        outputContains 'CC outcome: STORED'

        when: 'second run reuses the entry, restoring the flow action from the cache'
        configurationCacheRun 'help'

        then:
        configCache.assertStateLoaded()
        outputContains 'CC outcome: REUSED'
    }

    def "flow action observes DISCARDED when an incompatible task is scheduled"() {
        given:
        withOutcomePrintingPluginIn settingsFile
        buildFile '''
            tasks.register('incompatible') {
                notCompatibleWithConfigurationCache('declarative reason')
                doLast { }
            }
        '''

        when:
        configurationCacheRun 'incompatible'

        then:
        outputContains 'CC outcome: DISCARDED'
    }

    def "flow action observes NOT_STORED on a cache miss in read-only mode"() {
        given:
        withOutcomePrintingPluginIn settingsFile

        when:
        configurationCacheRun ENABLE_READ_ONLY_CACHE, 'help'

        then:
        outputContains 'CC outcome: NOT_STORED'
    }

    def "flow action observes the outcome when the build fails"() {
        given:
        withOutcomePrintingPluginIn settingsFile
        buildFile '''
            tasks.register('broken') {
                doLast { throw new GradleException('boom') }
            }
        '''

        when:
        configurationCacheFails 'broken'

        then:
        outputContains 'CC outcome: STORED'

        when:
        configurationCacheFails 'broken'

        then:
        outputContains 'CC outcome: REUSED'
    }

    def "accessing the outcome at configuration time fails"() {
        given:
        settingsFile """
            import org.gradle.api.flow.FlowProviders

            def flowProviders = gradle.services.get(FlowProviders)
            flowProviders.configurationCacheOutcome.get()
        """

        when:
        configurationCacheFails 'help'

        then:
        failureCauseContains "Cannot access the value of 'ConfigurationCacheOutcome' before it becomes available!"
    }

    def "using the outcome provider as a task input is reported as a problem"() {
        given:
        buildFile """
            import org.gradle.api.flow.FlowProviders

            abstract class PrintOutcomeTask extends DefaultTask {
                @Input
                abstract Property<org.gradle.api.configuration.ConfigurationCacheOutcome> getOutcome()

                @TaskAction
                void printIt() {
                    println("outcome: " + outcome.get())
                }
            }

            def flowProviders = gradle.services.get(FlowProviders)
            tasks.register('printOutcome', PrintOutcomeTask) {
                outcome = flowProviders.configurationCacheOutcome
            }
        """

        when:
        configurationCacheFails 'printOutcome'

        then: 'serializing the provider as a task input is reported as a problem'
        failure.assertHasFailures(2)
        failureDescriptionContains 'can only be used as input to flow actions'

        and: 'reading the provider at execution time fails because the value is not yet available'
        failureDescriptionContains "Execution failed for task ':printOutcome'"
        failureCauseContains "Cannot access the value of 'ConfigurationCacheOutcome' before it becomes available!"
    }

    def "flow action observes UPDATED when the entry is partially reused"() {
        given:
        withOutcomePrintingPluginIn settingsFile

        when:
        configurationCacheRun 'help'

        then:
        outputContains 'CC outcome: STORED'

        when: 'a project build script changes, invalidating only that project'
        buildFile '''
            tasks.register('other')
        '''
        configurationCacheRun 'help'

        then:
        outputContains 'CC outcome: UPDATED'
    }

    def "flow action of an included build observes the outcome of the whole invocation"() {
        given:
        settingsFile """
            includeBuild('included')
        """
        // The included build must contribute work to the invocation: flow actions of a build
        // without created projects are not part of the cache entry and do not run on a hit.
        buildFile """
            tasks.register('ok') {
                dependsOn gradle.includedBuild('included').task(':ok')
            }
        """
        withOutcomePrintingPluginIn file('included/settings.gradle')
        file('included/build.gradle') << '''
            tasks.register('ok')
        '''

        when:
        configurationCacheRun 'ok'

        then:
        outputContains 'CC outcome: STORED'

        when:
        configurationCacheRun 'ok'

        then:
        outputContains 'CC outcome: REUSED'
    }

    def "init script flow action can enforce configuration cache reuse"() {
        given:
        settingsFile 'rootProject.name = "root"'

        and: 'an init script that fails the build when reuse is expected but did not happen'
        def initScript = file('verify-cc.init.gradle')
        initScript << """
            import org.gradle.api.flow.*
            import org.gradle.api.configuration.ConfigurationCacheOutcome

            class VerifyCcReusePlugin implements Plugin<Gradle> {

                private final FlowScope flowScope
                private final FlowProviders flowProviders

                @Inject
                VerifyCcReusePlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }

                void apply(Gradle target) {
                    flowScope.always(VerifyCcReuse) {
                        parameters.outcome = flowProviders.configurationCacheOutcome
                    }
                }
            }

            class VerifyCcReuse implements FlowAction<Parameters> {

                interface Parameters extends FlowParameters {
                    @Input Property<ConfigurationCacheOutcome> getOutcome()
                }

                void execute(Parameters parameters) {
                    // Read the toggle here, at execution time of the flow action, so that it
                    // neither becomes a configuration input nor gets baked into the cache entry.
                    if (System.getenv('VERIFY_CC_REUSE') != null) {
                        def outcome = parameters.outcome.get()
                        if (outcome != ConfigurationCacheOutcome.REUSED) {
                            throw new GradleException("Expected the configuration cache entry to be reused but the outcome was: \$outcome")
                        }
                    }
                }
            }

            apply plugin: VerifyCcReusePlugin
        """

        when: 'first run stores the entry, verification is off'
        configurationCacheRun '-I', initScript.name, 'help'

        then:
        noExceptionThrown()

        when: 'second run reuses the entry, verification is on'
        executer.withEnvironmentVars(VERIFY_CC_REUSE: 'true')
        configurationCacheRun '-I', initScript.name, 'help'

        then:
        noExceptionThrown()

        when: 'the whole entry is invalidated and verification is on'
        settingsFile << '''
            println 'settings changed'
        '''
        executer.withEnvironmentVars(VERIFY_CC_REUSE: 'true')
        configurationCacheFails '-I', initScript.name, 'help'

        then:
        failureDescriptionContains 'Expected the configuration cache entry to be reused but the outcome was: STORED'
    }

    def "flow action of a GradleBuild nested build observes NOT_ENABLED"() {
        given:
        withOutcomePrintingPluginIn file('nested/settings.gradle')
        file('nested/build.gradle') << ''
        settingsFile ''
        buildFile """
            tasks.register('nested', GradleBuild) {
                dir = 'nested'
                tasks = ['help']
            }
        """

        when:
        configurationCacheRunLenient 'nested'

        then: 'the nested invocation has no configuration cache of its own'
        outputContains 'CC outcome: NOT_ENABLED'
    }

    private void withOutcomePrintingPluginIn(TestFile scriptFile) {
        scriptFile << """
            import org.gradle.api.flow.*
            import org.gradle.api.configuration.ConfigurationCacheOutcome

            class OutcomePrintingPlugin implements Plugin<Settings> {

                private final FlowScope flowScope
                private final FlowProviders flowProviders

                @Inject
                OutcomePrintingPlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }

                void apply(Settings target) {
                    flowScope.always(PrintOutcome) {
                        parameters.outcome = flowProviders.configurationCacheOutcome
                    }
                }
            }

            class PrintOutcome implements FlowAction<Parameters> {

                interface Parameters extends FlowParameters {
                    @Input Property<ConfigurationCacheOutcome> getOutcome()
                }

                void execute(Parameters parameters) {
                    println('CC outcome: ' + parameters.outcome.get())
                }
            }

            apply plugin: OutcomePrintingPlugin
        """
    }
}
