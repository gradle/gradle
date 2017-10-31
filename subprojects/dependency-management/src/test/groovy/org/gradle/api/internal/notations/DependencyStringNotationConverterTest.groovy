/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.notations

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.typeconversion.NotationParserBuilder
import spock.lang.Specification

public class DependencyStringNotationConverterTest extends Specification {
    def parser = new DependencyStringNotationConverter(DirectInstantiator.INSTANCE, DefaultExternalModuleDependency.class);

    def "with artifact"() {
        when:
        def d = parse(parser, 'org.gradle:gradle-core:4.4-beta2@mytype')

        then:
        d.name == 'gradle-core'
        d.group == 'org.gradle'
        d.version == '4.4-beta2'

        !d.force
        !d.transitive
        !d.changing

        d.artifacts.size() == 1
        d.artifacts.find { it.name == 'gradle-core' && it.classifier == null && it.type == 'mytype' }
    }

    def "with classified artifact"() {
        when:
        def d = parse(parser, 'org.gradle:gradle-core:10:jdk-1.4@zip')

        then:
        d.name == 'gradle-core'
        d.group == 'org.gradle'
        d.version == '10'

        !d.force
        !d.transitive
        !d.changing

        d.artifacts.size() == 1
        d.artifacts.find { it.name == 'gradle-core' && it.classifier == 'jdk-1.4' && it.type == 'zip' }
    }

    def "with classifier"() {
        when:
        def d = parse(parser, 'org.gradle:gradle-core:10:jdk-1.4')

        then:
        d.name == 'gradle-core'
        d.group == 'org.gradle'
        d.version == '10'
        d.transitive

        !d.force
        !d.changing

        d.artifacts.size() == 1
        d.artifacts.find { it.name == 'gradle-core' && it.classifier == 'jdk-1.4' &&
                it.type == DependencyArtifact.DEFAULT_TYPE && it.extension == DependencyArtifact.DEFAULT_TYPE }
    }

    def "with 3-element GString"() {
        when:
        def descriptor = 'org.gradle:gradle-core:1.0'
        def gstring = "$descriptor"
        def d = parse(parser, gstring)

        then:
        d.group == 'org.gradle'
        d.name == 'gradle-core'
        d.version == '1.0'
        d.transitive

        !d.force
        !d.changing
    }

    def "with no group"() {
        when:
        def d = parse(parser, ":foo:1.0")

        then:
        d.group == null
        d.name == 'foo'
        d.version == '1.0'
        d.transitive

        !d.force
        !d.changing
    }

    def "with no version"() {
        when:
        def d = parse(parser, "hey:foo:")

        then:
        d.group == 'hey'
        d.name == 'foo'
        d.version == null
        d.transitive

        !d.force
        !d.changing
    }

    def "with no version and no group"() {
        when:
        def d = parse(parser, ":foo:")

        then:
        d.group == null
        d.name == 'foo'
        d.version == null
        d.transitive

        !d.force
        !d.changing
    }

    def "can create client module"() {
        def parser = new DependencyStringNotationConverter(DirectInstantiator.INSTANCE, DefaultClientModule);

        when:
        def d = parse(parser, 'org.gradle:gradle-core:10')

        then:
        d instanceof DefaultClientModule
        d.name == 'gradle-core'
        d.group == 'org.gradle'
        d.version == '10'
        d.transitive

        !d.force
    }

    def "client module ignores the artifact only notation"() {
        def parser = new DependencyStringNotationConverter(DirectInstantiator.INSTANCE, DefaultClientModule);

        when:
        def d = parse(parser, 'org.gradle:gradle-core:10@jar')

        then:
        d instanceof DefaultClientModule
        d.name == 'gradle-core'
        d.group == 'org.gradle'
        d.version == '10@jar'
        d.transitive

        !d.force
        d.artifacts.size() == 0
    }

    def parse(def parser, def value) {
        return NotationParserBuilder.toType(Dependency).fromCharSequence(parser).toComposite().parseNotation(value)
    }
}
