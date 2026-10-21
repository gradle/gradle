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

package org.gradle.xdl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * The universe-composition hand-off to the sync model builders: what a build's schema universe is
 * made of is composed once by the settings apply and frozen into the build-scoped
 * {@code org.gradle.api.xdl.internal.UniverseCompositionHolder} service. An applied built-in
 * ecosystem's published library is an ordinary classpath root in it — the provider injects the
 * library into the settings classpath resolution (served by the distribution's embedded Maven
 * repository), so build evaluation and the sync/LSP universe see built-ins uniformly in the
 * resolution result. The service and the composition's whole reachable graph are API-layer types
 * on purpose: the model builders ride the sync CLI's init-script classpath, and only
 * API-parent-loader classes are the same {@code Class} for them and for the distribution. This
 * test pins the provider half of that contract against a real distribution, reading the frozen
 * composition exactly the way the model builders do; the composition-to-universe-members half is
 * unit-tested in {@code xdl-gradle-model-builders}.
 */
class XdlBuiltinEcosystemSyncModelIntegrationTest extends AbstractIntegrationSpec {

    private static final String PROBE = '''
        def holder = gradle.services.find(org.gradle.api.xdl.internal.UniverseCompositionHolder)
        println("xdl-holder-present=" + (holder != null))
        def composition = holder?.composition()
        println("xdl-composition-frozen=" + (composition != null))
        if (composition != null) {
            println("xdl-classpath-root-count=" + composition.classpathRoots.size())
            composition.classpathRoots.each { println("xdl-classpath-root-owner=" + it.owner) }
            println("xdl-bootstrap-count=" + composition.bootstrapSchemas.size())
        }
    '''

    def "the settings apply freezes the composed universe, with the applied ecosystems' libraries as ordinary classpath roots"() {
        given: 'a declarative settings applying the plugin-development ecosystem, probed by an imperative root build script'
        file('settings.gradle.xdl') << '''
            settings {
              plugins [
                { id "plugin-development-ecosystem" }
              ]
              rootProject { name "probe" }
            }
        '''
        buildFile << PROBE

        when:
        succeeds("help")

        then: 'the injected libraries arrive as resolution-owned roots: the applied module AND the common module it imports, at the distribution version'
        outputContains("xdl-composition-frozen=true")
        output.contains("xdl-classpath-root-owner=org.gradle:gradle-xdl-plugin-development:" + distribution.version.version)
        output.contains("xdl-classpath-root-owner=org.gradle:gradle-xdl-common-ecosystem:" + distribution.version.version)

        and: 'the composition carries the distribution bootstrap text, not just the roots'
        outputContains("xdl-bootstrap-count=1")
    }

    def "a routed build applying no ecosystem freezes a lean composition, not an absent one"() {
        given:
        file('settings.gradle.xdl') << '''
            settings {
              rootProject { name "probe" }
            }
        '''
        buildFile << PROBE

        when:
        succeeds("help")

        then: 'the frozen composition answers "applied, nothing resolved" — consumers fall back only on ABSENT, never on lean'
        outputContains("xdl-composition-frozen=true")
        outputContains("xdl-classpath-root-count=0")
        outputContains("xdl-bootstrap-count=1")
    }
}
