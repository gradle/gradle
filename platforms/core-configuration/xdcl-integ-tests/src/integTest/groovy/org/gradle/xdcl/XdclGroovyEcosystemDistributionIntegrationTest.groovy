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
 * The built-in XDCL Groovy ecosystem, end-to-end against a real distribution (forking). Groovy is a
 * SIBLING of the JVM ecosystem — it shares the common dependency/repository schema but not the JVM
 * schema — shipped and applied by id the same way. Applying {@code groovy-ecosystem} PULLS its schema
 * (and the common schema it imports) from the distribution via ModuleRegistry, so {@code groovyLibrary
 * { }} resolves and GroovyLibraryReaction configures a real Groovy build.
 */
class XdclGroovyEcosystemDistributionIntegrationTest extends AbstractIntegrationSpec {

    def "the built-in groovy-ecosystem plugin contributes its schema and configures a real Groovy library"() {
        given: 'a build that opts into the built-in Groovy ecosystem — no included build, no dependency resolution'
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "groovy-ecosystem" }
              ]
              rootProject { name "demo" }
            }
        '''

        and: 'an empty groovyLibrary — sources and groovyVersion come from the shipped ecosystem defaults'
        file('build.gradle.xdcl') << '''
            groovyLibrary {
            }
        '''

        when:
        succeeds("tasks", "--all")

        then: 'the plugin-shipped whole-list default supplied main and test, and the scalar groovyVersion default'
        outputContains("sources=[main, test]")
        outputContains("groovyVersion=4.0.3")

        and: 'the reaction registered the per-source-set Groovy tasks'
        outputContains("compileMainGroovy")
        outputContains("compileTestGroovy")
        outputContains("processMainResources")
        outputContains("processTestResources")

        and: 'plus a jar built from the main classes'
        outputContains("jar")
    }
}
