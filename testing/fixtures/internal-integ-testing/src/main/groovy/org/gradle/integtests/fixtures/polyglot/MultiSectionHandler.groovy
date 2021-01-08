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

import org.gradle.test.fixtures.dsl.GradleDsl

import java.util.function.Supplier

abstract class MultiSectionHandler<T> implements SectionBuilder {
    final List<SectionBuilder> sections = []

    public <B extends SectionBuilder> B newSection(@DelegatesTo.Target B section, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        sections << section
        BuilderSupport.applyConfiguration(spec, section)
        section
    }

    T section(Supplier<String> groovyDsl, Supplier<String> kotlinDsl) {
        sections << new GenericSection(groovyDsl, kotlinDsl)
        this
    }

    T section(String groovyDsl, String kotlinDsl) {
        section({ groovyDsl }, { kotlinDsl })
    }

    String getSectionName() {
        null
    }

    @Override
    String generateSection(GradleDsl dsl) {
        if (sectionName) {
            def name = escapeSectionName(dsl)
            """${name} {
            ${innerDsl(dsl)}
        }
        """
        }
    }

    protected String innerDsl(GradleDsl dsl) {
        sections.collect { it.generateSection(dsl) }.join("\n            ")
    }

    private String escapeSectionName(GradleDsl dsl) {
        switch (dsl) {
            case GradleDsl.GROOVY:
                if (sectionName in ['default', 'in', 'with']) {
                    // not extensive, add as needed
                    return "'$sectionName'"
                }
                break
            case GradleDsl.KOTLIN:
                if (sectionName in ['fun', 'in', 'with']) {
                    // not extensive, add as needed
                    return "'$sectionName'"
                }
                break
        }
        return sectionName
    }
}
