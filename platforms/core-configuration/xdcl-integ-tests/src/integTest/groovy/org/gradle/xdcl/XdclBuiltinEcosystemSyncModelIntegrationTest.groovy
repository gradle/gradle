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

package org.gradle.xdcl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * The universe-composition hand-off to the sync model builders: what a build's schema universe is
 * made of is composed once by the settings apply and frozen into the build-scoped
 * {@code org.gradle.api.xdcl.internal.UniverseCompositionHolder} service — above all the applied
 * built-in ecosystems' distribution schema jars, which never appear on the settings plugin
 * classpath (a distribution plugin bypasses artifact resolution), the one surface the sync/LSP
 * universe could otherwise derive members from. The service and the composition's whole reachable
 * graph are API-layer types on purpose: the model builders ride the sync CLI's init-script
 * classpath, and only API-parent-loader classes are the same {@code Class} for them and for the
 * distribution. This test pins the provider half of that contract against a real distribution,
 * reading the frozen composition exactly the way the model builders do; the
 * composition-to-universe-members half is unit-tested in {@code xdcl-gradle-model-builders}.
 */
class XdclBuiltinEcosystemSyncModelIntegrationTest extends AbstractIntegrationSpec {

    private static final String PROBE = '''
        def holder = gradle.services.find(org.gradle.api.xdcl.internal.UniverseCompositionHolder)
        println("xdcl-holder-present=" + (holder != null))
        def composition = holder?.composition()
        println("xdcl-composition-frozen=" + (composition != null))
        if (composition != null) {
            println("xdcl-builtin-ecosystem-ids=" + composition.builtinEcosystems*.ecosystemId.join(","))
            println("xdcl-builtin-jar-count=" + composition.builtinEcosystemSchemaJars.size())
            composition.builtinEcosystemSchemaJars.each { println("xdcl-builtin-jar=" + it.name) }
            println("xdcl-bootstrap-count=" + composition.bootstrapSchemas.size())
        }
    '''

    def "the settings apply freezes the composed universe, with the applied ecosystems' pulls attributed per id"() {
        given: 'a declarative settings applying the JVM ecosystem, probed by an imperative root build script'
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "java-ecosystem" }
              ]
              rootProject { name "probe" }
            }
        '''
        buildFile << PROBE

        when:
        succeeds("help")

        then: 'the pulled closure is handed over attributed to its ecosystem: the applied module AND the common module it imports'
        outputContains("xdcl-composition-frozen=true")
        outputContains("xdcl-builtin-ecosystem-ids=java-ecosystem")
        output.contains("xdcl-builtin-jar=gradle-xdcl-jvm-ecosystem-")
        output.contains("xdcl-builtin-jar=gradle-xdcl-common-ecosystem-")

        and: 'the composition carries the distribution bootstrap text, not just the jar list'
        outputContains("xdcl-bootstrap-count=1")
    }

    def "a routed build applying no ecosystem freezes a lean composition, not an absent one"() {
        given:
        file('settings.gradle.xdcl') << '''
            settings {
              rootProject { name "probe" }
            }
        '''
        buildFile << PROBE

        when:
        succeeds("help")

        then: 'the frozen composition answers "applied, nothing pulled" — consumers fall back only on ABSENT, never on lean'
        outputContains("xdcl-composition-frozen=true")
        outputContains("xdcl-builtin-jar-count=0")
        outputContains("xdcl-bootstrap-count=1")
    }
}
