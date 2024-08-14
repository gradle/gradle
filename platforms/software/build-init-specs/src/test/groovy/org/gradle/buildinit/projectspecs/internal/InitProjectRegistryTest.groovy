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


import org.gradle.builtinit.projectspecs.internal.internal.TestInitProjectGenerator
import org.gradle.builtinit.projectspecs.internal.internal.TestInitProjectSpec
import spock.lang.Specification

/**
 * Unit tests for {@link InitProjectRegistry}.
 */
class InitProjectRegistryTest extends Specification {
    def "registry provides loaded specs"() {
        given:
        def generator = new TestInitProjectGenerator()
        def spec1 = new TestInitProjectSpec("spec1")
        def spec2 = new TestInitProjectSpec("spec2")

        when:
        def registry = new InitProjectRegistry([(generator.class) : [spec1, spec2]])

        then: "loaded specs can be found"
        registry.getProjectGeneratorClass(spec1) == generator.class
        registry.getProjectGeneratorClass(spec2) == generator.class

        when:
        registry.getProjectGeneratorClass(new TestInitProjectSpec("spec3"))

        then: "not loaded specs can't be found"
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'spec3' was not found!"

        when:
        registry.getProjectGeneratorClass(new TestInitProjectSpec("spec1"))

        then: "specs with the same display name can't be found - we're finding specs by instance"
        e = thrown(IllegalStateException)
        e.message == "Spec: 'spec1' was not found!"
    }
}
