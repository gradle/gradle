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

import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.util.TestUtil
import spock.lang.Specification

class DependencyMapNotationConverterTest extends Specification {

    def parser = NotationParserBuilder.toType(ExternalModuleDependency).converter(new DependencyMapNotationConverter<DefaultExternalModuleDependency>(TestUtil.instantiatorFactory().decorateLenient(), DefaultExternalModuleDependency.class)).toComposite()

    def "with artifact"() {
        when:
        def d = parser.parseNotation([group: 'org.gradle', name: 'gradle-core', version: '4.4-beta2', ext: 'mytype'])

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
        def d = parser.parseNotation([group: 'org.gradle', name: 'gradle-core', version: '10', ext: 'zip', classifier: 'jdk-1.4'])

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
        def d = parser.parseNotation([group: 'org.gradle', name:'gradle-core', version:'10', classifier:'jdk-1.4']);

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
        d.artifacts.find { it.name == 'gradle-core' && it.classifier == 'jdk-1.4' &&
                it.type == DependencyArtifact.DEFAULT_TYPE && it.extension == DependencyArtifact.DEFAULT_TYPE }
    }

    def "with 3-element map"() {
        when:
        def d = parser.parseNotation([group: 'org.gradle', name:'gradle-core', version:'1.0']);

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

    def "with 3-element map and configuration"() {
        when:
        def d = parser.parseNotation([group: 'org.gradle', name:'gradle-core', version:'1.0', configuration:'compile']);

        then:
        d.group == 'org.gradle'
        d.name == 'gradle-core'
        d.version == '1.0'
        d.versionConstraint.requiredVersion == '1.0'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
        d.targetConfiguration == 'compile'
        d.transitive

        !d.force
        !d.changing
    }

    def "with default configuration"() {
        when:
        def d = parser.parseNotation([group: 'org.gradle', name:'gradle-core', version:'1.0', configuration:'default']);

        then:
        d.group == 'org.gradle'
        d.name == 'gradle-core'
        d.version == '1.0'
        d.versionConstraint.requiredVersion == '1.0'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
        d.targetConfiguration == 'default'
        d.transitive

        !d.force
        !d.changing
    }

    def "without default configuration"() {
        when:
        def d = parser.parseNotation([group: 'org.gradle', name:'gradle-core', version:'1.0']);

        then:
        d.group == 'org.gradle'
        d.name == 'gradle-core'
        d.version == '1.0'
        d.versionConstraint.requiredVersion == '1.0'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []
        d.targetConfiguration == null
        d.transitive

        !d.force
        !d.changing
    }

    def "with 3-element map and property"() {
        when:
        def d = parser.parseNotation([group: 'org.gradle', name:'gradle-core', version:'1.0', transitive:false]);

        then:
        d.group == 'org.gradle'
        d.name == 'gradle-core'
        d.version == '1.0'
        d.versionConstraint.requiredVersion == '1.0'
        d.versionConstraint.preferredVersion == ''
        d.versionConstraint.strictVersion == ''
        d.versionConstraint.rejectedVersions == []

        !d.transitive

        !d.force
        !d.changing
    }

    def "with no group and no version"() {
        when:
        def d = parser.parseNotation([name:'foo'])

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
}
