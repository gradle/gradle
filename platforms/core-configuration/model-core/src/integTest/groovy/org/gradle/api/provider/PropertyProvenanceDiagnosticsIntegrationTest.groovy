/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects

import static org.hamcrest.Matchers.containsString

@UnsupportedWithConfigurationCache(because = 'Diagnostic transport is D2')
@ToBeFixedForIsolatedProjects(because = 'D1 includes settings-origin project configuration fixtures')
class PropertyProvenanceDiagnosticsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument('-Dorg.gradle.internal.property-provenance=true')
    }

    def 'missing scalar reports plugin updates and explicit source with a shadowed convention'() {
        given:
        buildFile << """
            class SourcePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def value = project.objects.property(String)
                    project.extensions.add('tracked', value)
                    value.convention('secret fallback')
                    value.set(project.providers.provider { null })
                }
            }
            class UpdatePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tracked.replace { it.map { it + '-first' }.map { it + '-second' } }
                    project.tracked.replace { it.map { it + '-third' } }
                }
            }
            apply plugin: SourcePlugin
            apply plugin: UpdatePlugin
            tracked.get()
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('Cannot query the value of this property because it has no value available.')
        failure.assertThatCause(containsString('Failure trace to source'))
        failure.assertThatCause(containsString("at update map [plugin class 'UpdatePlugin'"))
        failure.assertThatCause(containsString("at update map -> map [plugin class 'UpdatePlugin'"))
        failure.assertThatCause(containsString("at explicit source [plugin class 'SourcePlugin'"))
        failure.assertThatCause(containsString('Shadowed configuration (not selected)'))
        !failure.error.contains('secret fallback')
    }

    def 'configuration trace is explicit lazy and survives finalization without automatic output'() {
        given:
        buildFile << """
            def value = objects.property(String)
            value.convention('secret fallback')
            value.set(providers.provider { throw new AssertionError('must not evaluate for explanation') })
            value.replace { it.map { throw new AssertionError('must not transform for explanation') } }
            ${explain ? 'println value.configurationTrace' : ''}
            def fixed = objects.property(String)
            fixed.set('secret fixed')
            fixed.finalizeValue()
            ${explain ? 'println fixed.shallowCopy().configurationTrace' : ''}
        """

        when:
        succeeds('help')

        then:
        output.contains('Configuration trace to source') == explain
        !output.contains('Failure trace to source')
        !output.contains('secret fallback')
        !output.contains('secret fixed')

        where:
        explain << [false, true]
    }

    def 'rejected plugin mutation preserves the original problem and accepted source'() {
        given:
        buildFile << """
            class SourcePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def value = project.objects.property(String)
                    project.extensions.add('tracked', value)
                    value.set('accepted secret')
                    value.disallowChanges()
                }
            }
            class CallerPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tracked.set('rejected secret')
                }
            }
            apply plugin: SourcePlugin
            apply plugin: CallerPlugin
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('The value for this property cannot be changed any further.')
        failure.assertThatCause(containsString("failed set [plugin class 'CallerPlugin'"))
        failure.assertThatCause(containsString("explicit source [plugin class 'SourcePlugin'"))
        !failure.error.contains('accepted secret')
        !failure.error.contains('rejected secret')
    }

    def 'finalized rejection reports unknown caller without losing the configured source'() {
        given:
        buildFile << """
            def value = objects.property(String)
            value.set('secret')
            value.finalizeValue()
            value.set('rejected')
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('The value for this property is final and cannot be changed any further.')
        failure.assertThatCause(containsString('failed set [unknown caller origin]'))
        failure.assertThatCause(containsString('at explicit source [build file'))
    }

    def 'settings configured project source survives missing finalization and copy'() {
        given:
        settingsFile << """
            gradle.beforeProject { project ->
                def value = project.objects.property(String)
                project.extensions.add('tracked', value)
                value.set(project.providers.provider { null })
            }
        """
        buildFile << """
            tracked.finalizeValue()
            tracked.shallowCopy().get()
        """

        when:
        fails('help')

        then:
        failure.assertThatCause(containsString('Failure trace to source'))
        failure.assertThatCause(containsString('at explicit source [settings file'))
        failure.assertThatCause(containsString("scope 'settings'"))
    }

    def 'disabled failures have the original message and no provenance report'() {
        given:
        executer.withArgument('-Dorg.gradle.internal.property-provenance=false')
        buildFile << """
            def value = objects.property(String)
            value.get()
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('Cannot query the value of this property because it has no value available.')
        !failure.error.contains('Failure trace to source')
        !failure.error.contains('Configuration trace to source')
    }
}
