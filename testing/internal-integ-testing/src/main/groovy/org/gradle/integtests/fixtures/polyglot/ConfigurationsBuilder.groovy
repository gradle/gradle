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

import java.lang.reflect.Array

@CompileStatic
class ConfigurationsBuilder extends MultiSectionHandler<ConfigurationsBuilder> implements SectionBuilder {
    final Set<String> alreadyCreated

    ConfigurationsBuilder(Set<String> alreadyCreated) {
        this.alreadyCreated = alreadyCreated
    }

    @Override
    String getSectionName() {
        "configurations"
    }

    Object propertyMissing(String name) {
        sections << new ConfigurationCreation(new GenericSection({ name }, {
            if (alreadyCreated.add(name)) {
                "val $name by configurations.creating"
            } else {
                "$name"
            }
        }))
        null
    }

    Object methodMissing(String name, Object args) {
        if (args.class.isArray()) {
            if (Array.getLength(args) == 1) {
                Object arg = Array.get(args, 0)
                if (arg instanceof Closure) {
                    withConfiguration(name, (Closure<?>) arg)
                }
            }
        }
        null
    }

    void withConfiguration(String name, @DelegatesTo(value = ConfigurationSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> config) {
        def spec = new ConfigurationSpec(name, { alreadyCreated.add(name) })
        sections << new ConfigurationCreation(spec)
        BuilderSupport.applyConfiguration(config, spec)
    }

    @Override
    String generateSection(GradleDsl dsl) {
        if (dsl == GradleDsl.GROOVY) {
            return super.generateSection(dsl)
        }
        def creations = sections.findAll { it.class == ConfigurationCreation }
        StringBuilder sb = new StringBuilder()
        creations.each {
            sb.append(it.generateSection(dsl)).append("\n")
        }
        sections.removeAll(creations)
        def configPart = sections ? super.generateSection(dsl) : ""
        return """$sb
${configPart}
"""
    }

    private static class ConfigurationCreation implements SectionBuilder {
        private final SectionBuilder delegate

        ConfigurationCreation(SectionBuilder delegate) {
            this.delegate = delegate
        }

        @Override
        String generateSection(GradleDsl dsl) {
            delegate.generateSection(dsl)
        }
    }
}
