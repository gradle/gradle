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

package org.gradle.kotlin.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.operations.execution.ExecuteWorkBuildOperationType
import spock.lang.Issue

class KotlinDslBuildCacheIntegrationTest extends AbstractIntegrationSpec {

    private static final String ACCESSOR_GENERATION_TYPE = "GENERATE_PROJECT_ACCESSORS"

    // Values are copied to make sure no accidental drift in property names and messages can happen
    private static final String ACCESSOR_CACHING_DISABLED_PROPERTY = "org.gradle.internal.kotlin-script-accessors-caching-disabled"
    private static final String SCRIPT_CACHING_DISABLED_PROPERTY = "org.gradle.internal.kotlin-script-caching-disabled"
    private static final String ACCESSOR_CACHING_DISABLED_MESSAGE = "Build caching of Kotlin script accessor generation disabled by property"
    private static final String SCRIPT_CACHING_DISABLED_MESSAGE = "Caching of Kotlin script compilation and Kotlin DSL accessors generation disabled by property"

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        // Own Gradle user home so the local build cache is writable and the immutable workspace is fresh
        requireOwnGradleUserHomeDir("Kotlin DSL accessor generation must run fresh, not be shared between tests")

        file("settings.gradle.kts") << '''
            rootProject.name = "accessor-cache"
        '''
        // Accessing the generated accessors (the `java { }` block) is what forces accessor generation;
        // a plugins-only script stays static and generates none.
        file("build.gradle.kts") << '''
            plugins {
                java
            }

            java {
            }
        '''
    }

    @Issue("37278")
    def "Kotlin DSL accessor generation is not build-cached by default"() {
        when:
        withBuildCache().run "help"

        then:
        def accessors = accessorBuildOperations()
        accessors.size() == 1
        accessors.first().result.cachingDisabledReasonCategory == "NOT_CACHEABLE"
        accessors.first().result.cachingDisabledReasonMessage == ACCESSOR_CACHING_DISABLED_MESSAGE
    }

    @Issue("37278")
    def "Kotlin DSL accessor generation caching can be re-enabled via the internal property"() {
        when:
        withBuildCache().run "-D${ACCESSOR_CACHING_DISABLED_PROPERTY}=false", "help"

        then:
        def accessors = accessorBuildOperations()
        accessors.size() == 1
        accessors.first().result.cachingDisabledReasonCategory == null
        accessors.first().result.cachingDisabledReasonMessage == null
    }

    @Issue("37278")
    def "disabling Kotlin script caching also disables accessor generation caching, for the shared reason"() {
        when: "the global script-caching switch is on while the accessor-specific switch is off"
        withBuildCache().run "-D${SCRIPT_CACHING_DISABLED_PROPERTY}=true", "-D${ACCESSOR_CACHING_DISABLED_PROPERTY}=false", "help"

        then: "accessor generation is disabled for the shared script-caching reason, not the accessor-specific one"
        def accessor = accessorBuildOperations().first()
        accessor.result.cachingDisabledReasonCategory == "NOT_CACHEABLE"
        accessor.result.cachingDisabledReasonMessage == SCRIPT_CACHING_DISABLED_MESSAGE
    }

    private accessorBuildOperations() {
        buildOperations.all(ExecuteWorkBuildOperationType).findAll { it.details.workType == ACCESSOR_GENERATION_TYPE }
    }
}
