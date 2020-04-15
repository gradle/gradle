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
abstract class AbstractDependenciesBuilder extends MultiSectionHandler implements SectionBuilder {

    void api(String notation) {
        sections << new DependencySpec("api", notation)
    }

    void implementation(String notation) {
        sections << new DependencySpec("implementation", notation)
    }

    Object methodMissing(String name, Object args) {
        sections << new DependencySpec(name, BuilderSupport.unwrap(args))
        null
    }

    SectionBuilder project(String path) {
        new GenericSection(
            { "project('$path')" },
            { "project(\"$path\")" },
        )
    }

    SectionBuilder project(Map<String, ?> params) {
        String groovyParams = params.collect { k, v ->
            "$k: '$v'"
        }.join(", ")
        String kotlinParams = params.collect { k, v ->
            "\"$k\" to \"$v\""
        }.join(", ")
        new GenericSection(
            { "project($groovyParams)" },
            { "project(mapOf($kotlinParams))" },
        )
    }

    SectionBuilder platform(SectionBuilder delegate) {
        new SimpleWrapper("platform", delegate)
    }

    SectionBuilder platform(String delegate) {
        platform(new SimpleSection("\"$delegate\""))
    }

    SectionBuilder enforcedPlatform(SectionBuilder delegate) {
        new SimpleWrapper("enforcedPlatform", delegate)
    }

    SectionBuilder enforcedPlatform(String delegate) {
        enforcedPlatform(new SimpleSection("\"$delegate\""))
    }

    static class DependencySpec extends AbstractSection {
        private final String targetConfiguration
        private final Object notation

        DependencySpec(String configuration, Object notation) {
            this.targetConfiguration = configuration
            this.notation = notation
        }

        private String formatNotation(GradleDsl dsl) {
            if (notation instanceof SectionBuilder) {
                def string = notation.generateSection(dsl)
                switch (dsl) {
                    case GradleDsl.GROOVY:
                        return string
                    case GradleDsl.KOTLIN:
                        return "($string)"
                }
            } else if (notation?.class.isArray()) {
                def len = Array.getLength(notation)
                Object last = Array.get(notation, len - 1)
                if (len==2 && last instanceof Closure) {
                    // configuration pattern
                    String notation = String.valueOf(Array.get(notation, 0))
                    switch (dsl) {
                        case GradleDsl.GROOVY:
                            notation = "('$notation')"
                            break
                        case GradleDsl.KOTLIN:
                            notation = "(\"$notation\")"
                            break
                    }
                    def details = new DetailedDependencySpec(notation)
                    BuilderSupport.applyConfiguration(last, details)
                    return details.generateSection(dsl)
                }
            } else {
                def string = "\"${String.valueOf(notation)}\""
                switch (dsl) {
                    case GradleDsl.GROOVY:
                        return string
                    case GradleDsl.KOTLIN:
                        return "($string)"
                }
            }
        }

        @Override
        String getGroovy() {
            """${targetConfiguration} ${formatNotation(GradleDsl.GROOVY)} """
        }

        @Override
        String getKotlin() {
            """${targetConfiguration} ${formatNotation(GradleDsl.KOTLIN)} """
        }
    }

    static class SimpleWrapper extends AbstractSection {
        private final String wrapper
        private final SectionBuilder section

        SimpleWrapper(String wrapper, SectionBuilder section) {
            this.wrapper = wrapper
            this.section = section
        }

        @Override
        String getGroovy() {
            "$wrapper(${section.generateSection(GradleDsl.GROOVY)})"
        }

        @Override
        String getKotlin() {
            "$wrapper(${section.generateSection(GradleDsl.KOTLIN)})"
        }
    }

    static class DetailedDependencySpec extends MultiSectionHandler<DetailedDependencySpec> {
        private final String baseNotation
        private VersionSpec version

        DetailedDependencySpec(String baseNotation) {
            this.baseNotation = baseNotation
        }

        @Override
        String getSectionName() {
            baseNotation
        }

        void because(String reason) {
            sections << new GenericSection(
                {
                    "because '$reason'"},
                {
                    "because(\"$reason\")"
                }
            )
        }

        void version(@DelegatesTo(value=VersionSpec, strategy=Closure.DELEGATE_FIRST) Closure<?> spec) {
            if (version == null) {
                version = new VersionSpec()
                sections << version
            }
            BuilderSupport.applyConfiguration(spec, version)
        }
    }
}
