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

package org.gradle.buildinit.projectspecs.internal

import org.gradle.api.logging.Logger
import org.gradle.buildinit.projectspecs.InitProjectSpec
import org.gradle.builtinit.projectspecs.internal.TestInitProjectGenerator
import org.gradle.builtinit.projectspecs.internal.TestInitProjectSource
import org.gradle.builtinit.projectspecs.internal.TestInitProjectSpec
import org.gradle.internal.logging.ToStringLogger
import spock.lang.Specification

/**
 * Unit tests for {@link InitProjectSpecLoader}.
 */
class InitProjectSpecLoaderTest extends Specification {
    private final Logger logger = new ToStringLogger()

    def "can load sources on classpath"() {
        given: "a source of project init specs on the classpath"
        InitProjectSpec spec = new TestInitProjectSpec("test", "Test Spec")
        TestInitProjectSource.addSpecs(spec)

        and:
        InitProjectSpecLoader loader = new InitProjectSpecLoader(Thread.currentThread().contextClassLoader, logger)

        when:
        def result = loader.loadProjectSpecs()

        then:
        result.size() == 1
        result.keySet().size() == 1
        result.keySet()[0] == TestInitProjectGenerator
        result.values().size() == 1
        result.values()[0].size() == 1
        result.values()[0][0] == spec
    }
}
