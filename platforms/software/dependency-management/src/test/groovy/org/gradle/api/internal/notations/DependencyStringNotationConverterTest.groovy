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

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.util.TestUtil
import org.gradle.util.internal.SimpleMapInterner
import spock.lang.Specification

class DependencyStringNotationConverterTest extends Specification {
    def parser = new DependencyStringNotationConverter(TestUtil.instantiatorFactory().decorateLenient(), DefaultExternalModuleDependency.class, SimpleMapInterner.notThreadSafe());

    def "with artifact"() {
        when:
        def d = parse(parser, 'org.gradle:gradle-core:4.4-beta2@mytype')

        then:
        d.name == 'gradle-core'
        d.group == 'org.gradle'
        d.version == '4.4-beta2'
        d.versionConstraint.requiredVersion == '4.4-beta2'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []

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
        d.versionConstraint.requiredVersion == '10'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []

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
        d.versionConstraint.requiredVersion == '10'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
        d.transitive

        !d.force
        !d.changing

        d.artifacts.size() == 1
        d.artifacts.find {
            it.name == 'gradle-core' && it.classifier == 'jdk-1.4' &&
                it.type == DependencyArtifact.DEFAULT_TYPE && it.extension == DependencyArtifact.DEFAULT_TYPE
        }
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
        d.versionConstraint.requiredVersion == '1.0'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
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
        d.versionConstraint.requiredVersion == '1.0'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
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
        d.versionConstraint.requiredVersion == ''
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
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
        d.versionConstraint.requiredVersion == ''
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
        d.transitive

        !d.force
        !d.changing
    }

    def "can create client module"() {
        def parser = new DependencyStringNotationConverter(TestUtil.instantiatorFactory().decorateLenient(), DefaultClientModule, SimpleMapInterner.notThreadSafe());

        when:
        def d = parse(parser, 'org.gradle:gradle-core:10')

        then:
        d instanceof DefaultClientModule
        d.name == 'gradle-core'
        d.group == 'org.gradle'
        d.version == '10'
        d.versionConstraint.requiredVersion == '10'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
        d.transitive

        !d.force
    }

    def "client module ignores the artifact only notation"() {
        def parser = new DependencyStringNotationConverter(TestUtil.instantiatorFactory().decorateLenient(), DefaultClientModule, SimpleMapInterner.notThreadSafe());

        when:
        def d = parse(parser, 'org.gradle:gradle-core:10@jar')

        then:
        d instanceof DefaultClientModule
        d.name == 'gradle-core'
        d.group == 'org.gradle'
        d.version == '10@jar'
        d.versionConstraint.requiredVersion == '10@jar'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
        d.transitive

        !d.force
        d.artifacts.size() == 0
    }

    def "parses short hand-notation #notation for strict dependencies"() {
        def parser = new DependencyStringNotationConverter(TestUtil.instantiatorFactory().decorateLenient(), DefaultClientModule, SimpleMapInterner.notThreadSafe());

        when:
        def d = parse(parser, "org.foo:bar:$notation")

        then:
        d instanceof ExternalModuleDependency
        d.group == 'org.foo'
        d.name == 'bar'
        d.versionConstraint.requiredVersion == strict
        d.versionConstraint.strictVersion == strict
        d.versionConstraint.preferredVersion == preferred

        where:
        notation          | strict       | preferred
        '1.0!!'           | '1.0'        | ""
        '[1.0, 2.0]!!'    | '[1.0, 2.0]' | ""
        '[1.0, 2.0]!!1.5' | '[1.0, 2.0]' | '1.5'
    }

    def "rejects short hand notation for strict if it starts with double-bang"() {
        def parser = new DependencyStringNotationConverter(TestUtil.instantiatorFactory().decorateLenient(), DefaultClientModule, SimpleMapInterner.notThreadSafe());

        when:
        parse(parser, "org.foo:bar:!!1.0")

        then:
        def t = thrown(InvalidUserCodeException)
        t.message == 'The strict version modifier (!!) must be appended to a valid version number'
    }

    def parse(def parser, def value) {
        return NotationParserBuilder.toType(Dependency).fromCharSequence(parser).toComposite().parseNotation(value)
    }
}
