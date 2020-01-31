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

package org.gradle.integtests.fixtures.polyglot

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.dsl.GradleDsl

@CompileStatic
class ConfigurationSpec extends MultiSectionHandler {
    private final String name

    ConfigurationSpec(String name) {
        this.name = name
    }

    @Override
    String getSectionName() {
        name
    }

    void extendsFrom(String... superConfs) {
        sections.add(new GenericSection({
            "extendsFrom ${superConfs.join(',')}"
        }, {
            "extendsFrom(${superConfs.collect { "configurations.getByName(\"$it\")" }.join(',')})"
        }))
    }

    @Override
    String generateSection(GradleDsl dsl) {
        switch (dsl) {
            case GradleDsl.GROOVY:
                return super.generateSection(dsl)
            case GradleDsl.KOTLIN:
                return """val $name by configurations.creating {
    ${innerDsl(dsl)}
}"""
        }

    }
}
