/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.MultiProjectBuilder
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.test.fixtures.dsl.GradleDsl

/**
 * An integration test which allows testing build scripts with both the
 * Groovy and Kotlin DSLs
 */
@CompileStatic
@PolyglotDslTest
@SkipDsl(dsl = GradleDsl.DECLARATIVE, because = "Declarative DSL needs more feature in order to run these tests")
class AbstractPolyglotIntegrationSpec extends AbstractIntegrationSpec implements PolyglotTestFixture {

    private MultiProjectBuilder projectBuilder

    def setup() {
        projectBuilder = new MultiProjectBuilder(currentDsl(), testDirectory)
    }

    /**
     * Configures a build spec, but doesn't generate the files.
     * @param spec
     */
    void buildSpec(@DelegatesTo(value=MultiProjectBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        Closure<?> clone = (Closure<?>) spec.clone()
        clone.delegate = projectBuilder
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone.call(projectBuilder)
    }

    /**
     * Configures a build spec and finally generates the files
     * @param spec
     */
    void writeSpec(@DelegatesTo(value=MultiProjectBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildSpec {
            buildSpec(spec)
            generate()
        }
    }
}
