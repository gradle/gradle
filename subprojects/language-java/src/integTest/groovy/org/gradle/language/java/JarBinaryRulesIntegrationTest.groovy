/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JarBinaryRulesIntegrationTest extends AbstractIntegrationSpec {
    def "rules applied on all Jar binaries get applied only once" () {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}

import javax.inject.Inject
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType

class SampleLibraryRules implements Plugin<Project> {
    private final ModelRegistry registry

    @Inject
    public SampleLibraryRules(ModelRegistry registry) {
        this.registry = registry
    }

    public void apply(Project project) {
        registry.root.applyToAllLinksTransitive(ModelType.of(JarBinarySpec), BinaryRules)
    }
}

class BinaryRules extends RuleSource {
    @Defaults
    void applyToBinaries(JarBinarySpec binary) {
        println "Binary: \${binary}"
    }
}

apply plugin: SampleLibraryRules

model {
    components {
        lib1(JvmLibrarySpec)
    }
}
"""

        when:
        succeeds "components"

        then:
        output.count("Binary: Jar 'lib1Jar'") == 1
    }
}
