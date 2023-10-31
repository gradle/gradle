/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.attributes

import org.gradle.internal.component.ResolutionFailureHandler

/**
 * Variant of the configuration attributes resolution integration test which makes use of the strongly typed attributes notation.
 */
class StronglyTypedConfigurationAttributesResolveIntegrationTest extends AbstractConfigurationAttributesResolveIntegrationTest {
    @Override
    String getTypeDefs() {
        '''
            interface Flavor extends Named {
            }

            enum BuildType {
                debug,
                release
            }

            def flavor = Attribute.of(Flavor)
            def buildType = Attribute.of(BuildType)
            def extra = Attribute.of('extra', String)

            allprojects {
               dependencies {
                   attributesSchema {
                      attribute(flavor)
                      attribute(buildType)
                      attribute(extra)
                   }
               }
            }
        '''
    }

    @Override
    String getDebug() {
        'attribute(buildType, BuildType.debug)'
    }

    @Override
    String getFree() {
        'attribute(flavor, objects.named(Flavor, "free"))'
    }

    @Override
    String getRelease() {
        'attribute(buildType, BuildType.release)'
    }

    @Override
    String getPaid() {
        'attribute(flavor, objects.named(Flavor, "paid"))'
    }

    def "resolution fails when two configurations use the same attribute name with different types"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies.attributesSchema {
                    attribute(buildType)
                    attribute(flavor)
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == ['b-default.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                       assert configurations._compileFreeRelease.collect { it.name } == ['b-default.jar']
                    }
                }
            }
            project(':b') {
                def flavorInteger = Attribute.of('flavor', Integer)
                def buildTypeInteger = Attribute.of('buildType', Integer)
                dependencies {
                    attributesSchema {
                        attribute(flavorInteger)
                        attribute(buildTypeInteger)
                    }
                }
                configurations {
                    create('default')
                    foo {
                        attributes { attribute(flavorInteger, 1); attribute(buildTypeInteger, 1) }
                    }
                    bar {
                        attributes { attribute(flavorInteger, 1); attribute(buildTypeInteger, 2) }
                    }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                artifacts {
                    'default' file('b-default.jar')
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause("Could not resolve project :b.")
        failure.assertHasCause("Unexpected type for attribute 'flavor' provided. Expected a value of type Flavor but found a value of type java.lang.Integer.")

        when:
        fails ':a:checkRelease'

        then:
        failure.assertHasCause("Could not resolve project :b.")
        failure.assertHasCause("Unexpected type for attribute 'flavor' provided. Expected a value of type Flavor but found a value of type java.lang.Integer.")
    }

    def "selects best compatible match using consumers disambiguation rules when multiple are compatible"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    details.compatible()
                }
            }
            class FlavorSelectionRule implements AttributeDisambiguationRule<Flavor> {
                void execute(MultipleCandidatesDetails<Flavor> details) {
                    assert details.candidateValues*.name as Set == ['ONE', 'TWO'] as Set
                    details.candidateValues.each { producerValue ->
                        if (producerValue.name == 'TWO') {
                            details.closestMatch(producerValue)
                        }
                    }
                }
            }

            project(':a') {
               dependencies {
                   attributesSchema {
                      attribute(flavor) {
                          compatibilityRules.add(FlavorCompatibilityRule)
                          disambiguationRules.add(FlavorSelectionRule)
                      }
                   }
               }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    def files = configurations._compileFreeDebug
                    doLast {
                       assert files.collect { it.name } == ['b-foo2.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, "ONE")) }
                    }
                    foo2 {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, "TWO")) }
                    }
                    bar {
                        attributes { $freeRelease }
                    }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task foo2Jar(type: Jar) {
                   archiveBaseName = 'b-foo2'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    foo2 foo2Jar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:foo2Jar', ':a:checkDebug')
    }

    def "selects configuration with requested value when multiple are compatible"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    details.compatible()
                }
            }

            project(':a') {
               dependencies {
                   attributesSchema {
                      attribute(flavor) {
                          compatibilityRules.add(FlavorCompatibilityRule)
                      }
                   }
               }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    def files = configurations._compileFreeDebug
                    doLast {
                       assert files.collect { it.name } == ['b-foo2.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, "FREE")) }
                    }
                    foo2 {
                        attributes { $freeDebug }
                    }
                    bar {
                        attributes { $freeRelease }
                    }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task foo2Jar(type: Jar) {
                   archiveBaseName = 'b-foo2'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    foo2 foo2Jar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:foo2Jar', ':a:checkDebug')
    }

    def "fails when multiple candidates are still available after disambiguation rules have been applied"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    details.compatible()
                }
            }
            class FlavorSelectionRule implements AttributeDisambiguationRule<Flavor> {
                void execute(MultipleCandidatesDetails<Flavor> details) {
                    details.candidateValues.each { producerValue ->
                        if (producerValue.name == "ONE") {
                            details.closestMatch(producerValue)
                        }
                    }
                }
            }

            project(':a') {
               dependencies.attributesSchema {
                  attribute(flavor) {
                      compatibilityRules.add(FlavorCompatibilityRule)
                      disambiguationRules.add(FlavorSelectionRule)
                  }
               }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == []
                    }
                }
            }
            project(':b') {
                configurations {
                    foo {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, "TWO")) }
                    }
                    foo2 {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, "ONE")) }
                    }
                    foo3 {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, "ONE")) }
                    }
                    bar {
                        attributes { $release; attribute(flavor, objects.named(Flavor, "ONE")) }
                    }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task foo2Jar(type: Jar) {
                   archiveBaseName = 'b-foo2'
                }
                task foo3Jar(type: Jar) {
                   archiveBaseName = 'b-foo3'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                artifacts {
                    foo fooJar
                    foo2 foo2Jar
                    bar barJar
                }
            }

        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause """The consumer was configured to find attribute 'flavor' with value 'free', attribute 'buildType' with value 'debug'. However we cannot choose between the following variants of project :b:
  - foo2
  - foo3
All of them match the consumer attributes:
  - Variant 'foo2' capability test:b:unspecified declares attribute 'buildType' with value 'debug', attribute 'flavor' with value 'ONE'
  - Variant 'foo3' capability test:b:unspecified declares attribute 'buildType' with value 'debug', attribute 'flavor' with value 'ONE'
The following variants were also considered but didn't match the requested attributes:
  - Variant 'bar' capability test:b:unspecified declares attribute 'flavor' with value 'ONE':
      - Incompatible because this component declares attribute 'buildType' with value 'release' and the consumer needed attribute 'buildType' with value 'debug'"""
    }

    def "can select best compatible match when single best matches are found on individual attributes"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    details.compatible()
                }
            }
            class FlavorSelectionRule implements AttributeDisambiguationRule<Flavor> {
                void execute(MultipleCandidatesDetails<Flavor> details) {
                    details.candidateValues.each { producerValue ->
                        if (producerValue.name == 'TWO') {
                            details.closestMatch(producerValue)
                        }
                    }
                }
            }
            class BuildTypeCompatibilityRule implements AttributeCompatibilityRule<BuildType> {
                void execute(CompatibilityCheckDetails<BuildType> details) {
                    details.compatible()
                }
            }
            class SelectDebugRule implements AttributeDisambiguationRule<BuildType> {
                void execute(MultipleCandidatesDetails<BuildType> details) {
                    details.closestMatch(BuildType.debug)
                }
            }

            project(':a') {
               dependencies.attributesSchema {
                  attribute(flavor) {
                      compatibilityRules.add(FlavorCompatibilityRule)
                      disambiguationRules.add(FlavorSelectionRule)
                  }

                  // for testing purposes, this strategy says that all build types are compatible, but returns the debug value as best
                  attribute(buildType) {
                     compatibilityRules.add(BuildTypeCompatibilityRule)
                     disambiguationRules.add(SelectDebugRule)
                  }
               }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    def files = configurations._compileFreeDebug
                    doLast {
                       assert files.collect { it.name } == ['b-foo2.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo {
                        attributes { attribute(buildType, BuildType.debug); attribute(flavor, objects.named(Flavor, "ONE")) }
                    }
                    foo2 {
                        attributes { attribute(buildType, BuildType.debug); attribute(flavor, objects.named(Flavor, "TWO")) }
                    }
                    bar {
                        attributes { attribute(buildType, BuildType.release); attribute(flavor, objects.named(Flavor, "ONE")) }
                    }
                    bar2 {
                        attributes { attribute(buildType, BuildType.release); attribute(flavor, objects.named(Flavor, "TWO")) }
                    }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task foo2Jar(type: Jar) {
                   archiveBaseName = 'b-foo2'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                task bar2Jar(type: Jar) {
                   archiveBaseName = 'b-bar2'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    foo2 foo2Jar
                    bar barJar
                    bar2 bar2Jar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:foo2Jar', ':a:checkDebug')
    }

    def "can select best compatible match based on requested value"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    details.compatible()
                }
            }
            class FlavorSelectionRule implements AttributeDisambiguationRule<Flavor> {
                void execute(MultipleCandidatesDetails<Flavor> details) {
                    if (details.consumerValue == null) {
                        details.closestMatch(details.candidateValues.find { it.name == 'ONE' })
                    } else if (details.consumerValue.name == 'free') {
                        details.closestMatch(details.candidateValues.find { it.name == 'TWO' })
                    }
                }
            }

            project(':a') {
               dependencies.attributesSchema {
                  attribute(flavor) {
                      compatibilityRules.add(FlavorCompatibilityRule)
                      disambiguationRules.add(FlavorSelectionRule)
                  }
               }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileDebug.attributes { $debug }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileDebug project(':b')
                }
                task checkFreeDebug(dependsOn: configurations._compileFreeDebug) {
                    def files = configurations._compileFreeDebug
                    doLast {
                       assert files.collect { it.name } == ['b-foo2.jar']
                    }
                }
                task checkDebug(dependsOn: configurations._compileDebug) {
                    def files = configurations._compileDebug
                    doLast {
                       assert files.collect { it.name } == ['b-foo.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo {
                        attributes { attribute(flavor, objects.named(Flavor, "ONE")) }
                    }
                    foo2 {
                        attributes { attribute(flavor, objects.named(Flavor, "TWO")) }
                    }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task foo2Jar(type: Jar) {
                   archiveBaseName = 'b-foo2'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    foo2 foo2Jar
                }
            }

        """

        when:
        run ':a:checkFreeDebug'

        then:
        result.assertTasksExecuted(':b:foo2Jar', ':a:checkFreeDebug')

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')
    }

    def "producer can apply additional compatibility rules when consumer does not have an opinion for attribute known to both"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class DoNothingRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                }
            }
            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    if (details.consumerValue.name == 'free' && details.producerValue.name == 'preview') {
                        details.compatible()
                    }
                }
            }

            project(':a') {
                dependencies.attributesSchema {
                    attribute(flavor) {
                        compatibilityRules.add(DoNothingRule)
                    }
                }
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    def files = configurations._compileFreeDebug
                    doLast {
                       assert files.collect { it.name } == ['b-bar.jar']
                    }
                }
            }
            project(':b') {
                dependencies.attributesSchema {
                    attribute(flavor) {
                        compatibilityRules.add(FlavorCompatibilityRule)
                    }
                }
                configurations {
                    foo {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, "release")) }
                    }
                    bar {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, "preview")) }
                    }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkDebug')
    }

    def "consumer can veto producers view of compatibility"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class VetoRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    if (details.producerValue.name == 'preview') {
                        details.incompatible()
                    }
                }
            }
            class EverythingIsCompatibleRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    details.compatible()
                }
            }

            project(':a') {
                dependencies.attributesSchema {
                    attribute(flavor) {
                        compatibilityRules.add(VetoRule)
                    }
                }
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    def files = configurations._compileFreeDebug
                    doLast {
                       assert files.collect { it.name } == ['b-bar.jar']
                    }
                }
            }
            project(':b') {
                dependencies.attributesSchema {
                    attribute(flavor) {
                        compatibilityRules.add(EverythingIsCompatibleRule)
                    }
                }
                configurations {
                    foo {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, 'preview')) }
                    }
                    bar {
                        attributes { $debug; attribute(flavor, objects.named(Flavor, 'any')) }
                    }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkDebug')
    }

    def "producer can apply disambiguation for attribute known to both"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorSelectionRule implements AttributeDisambiguationRule<Flavor> {
                void execute(MultipleCandidatesDetails<Flavor> details) {
                    details.closestMatch(details.candidateValues.sort { it.name }.last())
                }
            }

            project(':a') {
                configurations {
                    compile.attributes { $debug }
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile) {
                    def files = configurations.compile
                    doLast {
                       assert files.collect { it.name } == ['b-bar.jar']
                    }
                }
            }

            project(':b') {
                dependencies.attributesSchema {
                    attribute(flavor) {
                        disambiguationRules.add(FlavorSelectionRule)
                    }
                }
                configurations {
                    foo.attributes { $free; $debug }
                    bar.attributes { $paid; $debug }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }
        """

        when:
        run ':a:check'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:check')
    }

    def "producer can apply disambiguation for attribute not known to consumer"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class SelectionRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    details.closestMatch(details.candidateValues.sort { it }.first())
                }
            }

            def platform = Attribute.of('platform', String)

            project(':a') {
                configurations {
                    compile.attributes { $debug }
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile) {
                    def files = configurations.compile
                    doLast {
                       assert files.collect { it.name } == ['b-bar.jar']
                    }
                }
            }
            project(':b') {
                dependencies.attributesSchema.attribute(platform) {
                    disambiguationRules.add(SelectionRule)
                }
                configurations {
                    foo.attributes { attribute(platform, 'b'); $debug }
                    bar.attributes { attribute(platform, 'a'); $debug }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:check'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:check')
    }

    def "producer can apply disambiguation when consumer does not define any attributes"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class SelectionRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    details.closestMatch(details.candidateValues.sort { it }.first())
                }
            }

            def platform = Attribute.of('platform', String)

            project(':a') {
                configurations {
                    compile
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile) {
                    def files = configurations.compile
                    doLast {
                       assert files.collect { it.name } == ['b-bar.jar']
                    }
                }
            }
            project(':b') {
                dependencies.attributesSchema.attribute(platform) {
                    disambiguationRules.add(SelectionRule)
                }
                configurations {
                    foo.attributes { attribute(platform, 'b'); $debug }
                    bar.attributes { attribute(platform, 'a'); $debug }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:check'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:check')
    }

    def "both dependencies will choose the same default value"() {
        given:
        createDirs("a", "b", "c")
        file('settings.gradle') << "include 'a', 'b', 'c'"
        buildFile << """
            enum Arch {
               x86,
               arm64
            }
            def arch = Attribute.of(Arch)
            def dummy = Attribute.of('dummy', String)

            allprojects {
               dependencies {
                   attributesSchema {
                      attribute(dummy)
                   }
               }
            }

            project(':b') {
               dependencies.attributesSchema {
                    attribute(arch) {
                       disambiguationRules.pickLast { a,b -> a<=>b }
                  }
               }
            }
            project(':c') {
                dependencies.attributesSchema {
                    attribute(arch) {
                       disambiguationRules.pickLast { a,b -> a<=>b }
                    }
                }
            }

            project(':a') {
                configurations {
                    compile.attributes { attribute(dummy, 'dummy') }
                }
                dependencies {
                    compile project(':b')
                    compile project(':c')
                }
                task check(dependsOn: configurations.compile) {
                    def files = configurations.compile
                    doLast {
                       assert files.collect { it.name } == ['b-bar.jar', 'c-bar.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo.attributes { attribute(arch, Arch.x86); attribute(dummy, 'dummy') }
                    bar.attributes { attribute(arch, Arch.arm64); attribute(dummy, 'dummy') }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }
            project(':c') {
                configurations {
                    foo.attributes { attribute(arch, Arch.x86); attribute(dummy, 'dummy') }
                    bar.attributes { attribute(arch, Arch.arm64); attribute(dummy, 'dummy') }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'c-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'c-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:check'

        then:
        result.assertTasksExecuted(':b:barJar', ':c:barJar', ':a:check')
    }

    def "can inject configuration into compatibility and disambiguation rules"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                String value

                @javax.inject.Inject
                FlavorCompatibilityRule(String value) { this.value = value }

                void execute(CompatibilityCheckDetails<Flavor> details) {
                    if (details.producerValue.name == value) {
                        details.compatible()
                    }
                }
            }

            class BuildTypeSelectionRule implements AttributeDisambiguationRule<BuildType> {
                BuildType value

                @javax.inject.Inject
                BuildTypeSelectionRule(BuildType value) { this.value = value }
                void execute(MultipleCandidatesDetails<BuildType> details) {
                    if (details.candidateValues.contains(value)) {
                        details.closestMatch(value)
                    }
                }
            }

            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(flavor) {
                            compatibilityRules.add(FlavorCompatibilityRule) { params("full") }
                        }
                        attribute(buildType) {
                            disambiguationRules.add(BuildTypeSelectionRule) { params(BuildType.debug) }
                        }
                    }
                }
            }

            project(':a') {
                configurations {
                    compile { attributes { $free } }
                }
                dependencies {
                    compile project(':b')
                }
                task checkDebug(dependsOn: configurations.compile) {
                    def files = configurations.compile
                    doLast {
                        // Compatibility rules select paid flavors, disambiguation rules select debug
                        assert files.collect { it.name } == ['b-foo2.jar']
                    }
                }
            }
            project(':b') {
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task foo2Jar(type: Jar) {
                   archiveBaseName = 'b-foo2'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                configurations {
                    c1 { attributes { attribute(flavor, objects.named(Flavor, 'preview')); $debug } }
                    c2 { attributes { attribute(flavor, objects.named(Flavor, 'preview')); $release } }
                    c3 { attributes { attribute(flavor, objects.named(Flavor, 'full')); $debug } }
                    c4 { attributes { attribute(flavor, objects.named(Flavor, 'full')); $release } }
                }
                artifacts {
                    c1 fooJar
                    c2 fooJar
                    c3 foo2Jar
                    c4 barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:foo2Jar', ':a:checkDebug')
    }

    def "user receives reasonable error message when compatibility rule cannot be created"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                FlavorCompatibilityRule(String thing) { }
                void execute(CompatibilityCheckDetails<Flavor> details) {
                }
            }

            allprojects {
                dependencies.attributesSchema {
                    attribute(buildType)
                    attribute(flavor) {
                        compatibilityRules.add(FlavorCompatibilityRule)
                    }
                }
            }

            project(':a') {
                configurations {
                    compile.attributes { $free; $debug }
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile) {
                    doLast {
                       configurations.compile.files
                    }
                }
            }
            project(':b') {
                configurations {
                    bar.attributes { $paid; $debug }
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                artifacts {
                    bar barJar
                }
            }

        """

        when:
        fails("a:check")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a:check'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':a:compile'.")
        failure.assertHasCause("Could not resolve project :b.")
        failure.assertHasCause("Could not determine whether value paid is compatible with value free using FlavorCompatibilityRule.")
        failure.assertHasCause("Could not create an instance of type FlavorCompatibilityRule.")
        failure.assertHasCause("The constructor for type FlavorCompatibilityRule should be annotated with @Inject.")
    }

    def "user receives reasonable error message when compatibility rule fails"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    throw new RuntimeException("broken!")
                }
            }

            allprojects {
                dependencies.attributesSchema {
                    attribute(buildType)
                    attribute(flavor) {
                        compatibilityRules.add(FlavorCompatibilityRule)
                    }
                }
            }

            project(':a') {
                configurations {
                    compile.attributes { $free; $debug }
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile) {
                    doLast {
                       configurations.compile.files
                    }
                }
            }
            project(':b') {
                configurations {
                    bar.attributes { $paid; $debug }
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                artifacts {
                    bar barJar
                }
            }

        """

        when:
        fails("a:check")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a:check'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':a:compile'.")
        failure.assertHasCause("Could not resolve project :b.")
        failure.assertHasCause("Could not determine whether value paid is compatible with value free using FlavorCompatibilityRule.")
        failure.assertHasCause("broken!")
    }

    def "user receives reasonable error message when disambiguation rule cannot be created"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    details.compatible()
                }
            }

            class FlavorSelectionRule implements AttributeDisambiguationRule<Flavor> {
                FlavorSelectionRule(String thing) {
                }
                void execute(MultipleCandidatesDetails<Flavor> details) {
                }
            }

            allprojects {
                dependencies.attributesSchema {
                    attribute(buildType)
                    attribute(flavor) {
                        compatibilityRules.add(FlavorCompatibilityRule)
                        disambiguationRules.add(FlavorSelectionRule)
                    }
                }
            }

            project(':a') {
                configurations {
                    compile.attributes { $debug }
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile) {
                    doLast {
                       configurations.compile.files
                    }
                }
            }
            project(':b') {
                configurations {
                    foo.attributes { $free; $debug }
                    bar.attributes { $paid; $debug }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        fails("a:check")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a:check'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':a:compile'.")
        failure.assertHasCause("Could not resolve project :b.")
        failure.assertHasCause("Could not select value from candidates [free, paid] using FlavorSelectionRule.")
        failure.assertHasCause("Could not create an instance of type FlavorSelectionRule.")
        failure.assertHasCause("The constructor for type FlavorSelectionRule should be annotated with @Inject.")
    }

    def "user receives reasonable error message when disambiguation rule fails"() {
        given:
        createDirs("a", "b")
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
                void execute(CompatibilityCheckDetails<Flavor> details) {
                    details.compatible()
                }
            }

            class FlavorSelectionRule implements AttributeDisambiguationRule<Flavor> {
                void execute(MultipleCandidatesDetails<Flavor> details) {
                    throw new RuntimeException("broken!")
                }
            }

            allprojects {
                dependencies.attributesSchema {
                    attribute(buildType)
                    attribute(flavor) {
                        compatibilityRules.add(FlavorCompatibilityRule)
                        disambiguationRules.add(FlavorSelectionRule)
                    }
                }
            }

            project(':a') {
                configurations {
                    compile.attributes { $debug }
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile) {
                    doLast {
                       configurations.compile.files
                    }
                }
            }
            project(':b') {
                configurations {
                    foo.attributes { $free; $debug }
                    bar.attributes { $paid; $debug }
                }
                task fooJar(type: Jar) {
                   archiveBaseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   archiveBaseName = 'b-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        fails("a:check")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a:check'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':a:compile'.")
        failure.assertHasCause("Could not resolve project :b.")
        failure.assertHasCause("Could not select value from candidates [free, paid] using FlavorSelectionRule.")
        failure.assertHasCause("broken!")
    }
}
