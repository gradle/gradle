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
package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.maven.MavenFileRepository

import static org.gradle.util.internal.GUtil.toCamelCase

abstract class AbstractDependencyMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest {
    @Override
    String getTestConfiguration() { variantToTest }

    /**
     * Does the published metadata provide variants with attributes? Eventually all metadata should do that.
     * For Ivy and Maven POM metadata, the variants and attributes should be derived from configurations and scopes.
     */
    boolean getPublishedModulesHaveAttributes() { gradleMetadataPublished }

    String getVariantToTest() {
        if (gradleMetadataPublished || useIvy()) {
            'customVariant'
        } else {
            'runtime'
        }
    }

    def setup() {
        repository {
            'org.test:moduleA:1.0'() {
                variant 'customVariant', [format: 'custom']
            }
            'org.test:moduleB:1.0'()
        }

        buildFile << """
            configurations { $variantToTest { attributes { attribute(Attribute.of('format', String), 'custom') } } }

            dependencies {
                $variantToTest group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }
        """
    }

    def "#thing can be added using #notation notation"() {
        when:
        buildFile << """
            class ModifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        with${toCamelCase(thing)} {
                            add $declaration
                        }
                    }
                }
            }

            dependencies {
                $variantToTest 'org.test:moduleB'
                components {
                    withModule('org.test:moduleA', ModifyRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleB', 'org.test:moduleB:1.0') {
                    maybeByConstraint()
                }
                module("org.test:moduleA:1.0:$expectedVariant") {
                    if (thing == "dependencies") {
                        edge('org.test:moduleB:1.0', 'org.test:moduleB:1.0')
                    } else {
                        constraint('org.test:moduleB:1.0', 'org.test:moduleB:1.0')
                    }
                }
            }
        }

        where:
        thing                    | notation | declaration
        "dependency constraints" | "string" | "'org.test:moduleB:1.0'"
        "dependency constraints" | "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
        "dependencies"           | "string" | "'org.test:moduleB:1.0'"
        "dependencies"           | "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
    }

    def "#thing can be added to a new variant"() {
        when:
        buildFile << """
            class ModifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.addVariant("new") {
                        with${toCamelCase(thing)} {
                            add 'org.test:moduleB:1.0'
                        }
                        withCapabilities {
                            removeCapability("org.test", "moduleA")
                            addCapability("all", "new", "1.0")
                        }
                    }
                }
            }

            dependencies {
                $variantToTest 'org.test:moduleB'
                $variantToTest('org.test:moduleA:1.0') {
                    capabilities { requireCapability("all:new") }
                }
                components {
                    withModule('org.test:moduleA', ModifyRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleB', 'org.test:moduleB:1.0') {
                    maybeByConstraint()
                }
                module("org.test:moduleA:1.0:$expectedVariant")
                module("org.test:moduleA:1.0:new") {
                    if (thing == "dependencies") {
                        edge('org.test:moduleB:1.0', 'org.test:moduleB:1.0')
                    } else {
                        constraint('org.test:moduleB:1.0', 'org.test:moduleB:1.0')
                    }
                }
            }
        }

        where:
        thing                    | _
        "dependency constraints" | _
        "dependencies"           | _
    }

    def "#thing can be added and configured using #notation notation"() {
        when:
        buildFile << """
            class ModifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        with${toCamelCase(thing)} {
                            add($declaration) {
                                it.version { strictly '1.0' }
                            }
                        }
                    }
                }
            }

            dependencies {
                $variantToTest 'org.test:moduleB'
                components {
                    withModule('org.test:moduleA', ModifyRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleB', 'org.test:moduleB:1.0') {
                    maybeByConstraint()
                }
                module("org.test:moduleA:1.0:$expectedVariant") {
                    if (thing == "dependencies") {
                        edge('org.test:moduleB:{strictly 1.0}', 'org.test:moduleB:1.0')
                    } else {
                        constraint('org.test:moduleB:{strictly 1.0}', 'org.test:moduleB:1.0')
                    }
                }
            }
        }

        where:
        thing                    | notation | declaration
        "dependency constraints" | "string" | "'org.test:moduleB:1.0'"
        "dependency constraints" | "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
        "dependencies"           | "string" | "'org.test:moduleB:1.0'"
        "dependencies"           | "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
    }

    def "dependencies can be removed"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
            }
        }

        when:
        buildFile << """
            class ModifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        withDependencies {
                            removeAll { it.versionConstraint.requiredVersion == '1.0' }
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleA', ModifyRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant")
            }
        }
    }

    def "dependency constraints can be removed"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                constraint 'org.test:moduleB:2.0'
            }
        }

        when:
        buildFile << """
            class ModifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        withDependencyConstraints {
                            removeAll { it.versionConstraint.requiredVersion == '2.0' }
                        }
                    }
                }
            }

            dependencies {
                $variantToTest 'org.test:moduleB:1.0'
                components {
                    withModule('org.test:moduleA', ModifyRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()

            }
            'org.test:moduleB'() {
                version('1.0') {
                    expectGetMetadata()
                    expectGetArtifact()
                }
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleB:1.0")
                module("org.test:moduleA:1.0:$expectedVariant")
            }
        }
    }

    def "#thing modifications are visible in the next rule"() {
        when:
        buildFile << """
            class AddRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        with${toCamelCase(thing)} { d ->
                            assert d.size() == 0
                            d.add 'org.test:moduleB:1.0'
                        }
                    }
                }
            }

            class RemoveRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        with${toCamelCase(thing)} { d ->
                            assert d.size() == 1
                            d.removeAll { true }
                        }
                    }
                }
            }

            class VerifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        with${toCamelCase(thing)} { d ->
                            assert d.size() == 0
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleA', AddRule)
                    withModule('org.test:moduleA', RemoveRule)
                    all(VerifyRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant")
            }
        }

        where:
        thing                    | _
        "dependencies"           | _
        "dependency constraints" | _
    }

    def "can set version on dependency using #keyword"() {
        given:
        repository {
            'org.test:moduleA:1.0'() {
                dependsOn 'org.test:moduleB'
            }
        }

        when:
        buildFile << """
            class VersionSettingRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        withDependencies {
                            it.each {
                                it.version {
                                    require ''
                                    ${keyword} '1.0'
                                }
                            }
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleA', VersionSettingRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        def versionConstraint = keyword == 'require' ? '1.0' : "{${keyword} 1.0}"
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    edge('org.test:moduleB:' + versionConstraint, 'org.test:moduleB:1.0')
                }
            }
        }

        where:
        keyword << ["prefer", "require", "strictly"]
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "can set version on dependency constraint"() {
        given:
        repository {
            'org.test:moduleA:1.0'() {
                constraint 'org.test:moduleB:0.1'
            }
        }

        when:
        buildFile << """
            class VersionSettingRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        withDependencyConstraints {
                            it.each {
                                it.version { require '1.0' }
                            }
                        }
                    }
                }
            }

            dependencies {
                $variantToTest 'org.test:moduleB'
                components {
                    withModule('org.test:moduleA', VersionSettingRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleB', 'org.test:moduleB:1.0') {
                    byConstraint(null)
                }
                module("org.test:moduleA:1.0:$expectedVariant") {
                    constraint('org.test:moduleB:1.0', 'org.test:moduleB:1.0')
                }
            }
        }
    }


    def "changing dependencies in one variant leaves other variants untouched"() {
        when:
        buildFile << """
            class ModifyDepRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("default") {
                        withDependencies {
                            add('org.test:moduleB:1.0')
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleA', ModifyDepRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                variant("default", ['some':'other'])
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant")
            }
        }
    }

    def "can update all variants at once"() {
        when:
        buildFile << """
            class ModifyDepRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withDependencies {
                            add('org.test:moduleB:1.0')
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleA', ModifyDepRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                variant("default", ['some':'other'])
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module('org.test:moduleB:1.0')
                }
            }
        }
    }

    def "#thing of transitive dependencies can be changed"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0' {
                variant 'customVariant', [format: 'custom']
            }
            'org.test:moduleC:1.0'()
        }

        when:
        def transitiveSelectedVariant = !gradleMetadataPublished && useIvy()? 'default' : variantToTest
        buildFile << """
            class ModifyDepRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('$transitiveSelectedVariant') {
                        with${toCamelCase(thing)} { d ->
                            add('org.test:moduleC:1.0')
                        }
                    }
                }
            }

            dependencies {
                $variantToTest 'org.test:moduleC'
                components {
                    withModule('org.test:moduleB', ModifyDepRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleC:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleC', 'org.test:moduleC:1.0') {
                    maybeByConstraint()
                }
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0") {
                        if (thing == "dependencies") {
                            edge('org.test:moduleC:1.0', 'org.test:moduleC:1.0')
                        } else {
                            constraint('org.test:moduleC:1.0', 'org.test:moduleC:1.0')
                        }
                    }
                }
            }
        }

        where:
        thing                    | _
        "dependencies"           | _
        "dependency constraints" | _
    }

    def "attribute matching is used to select a variant of the dependency's target if the dependency was added by a rule"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0' {
                variant 'customVariant', [format: 'custom']
            }
            'org.test:moduleD:1.0'()
        }

        def mavenGradleRepo = new MavenFileRepository(file("maven-gradle-repo"))
        buildFile << """
            repositories {
                maven {
                    url "$mavenGradleRepo.uri"
                }
            }
        """
        //All dependencies added by rules are of type GradleDependencyMetadata and thus attribute matching is used for selecting the variant/configuration of the dependency's target.
        //Here we add a module with Gradle metadata which defines a variant that uses the same attributes declared in the build script (format: "custom").
        //The dependency to this module is then added using the rule and thus is matched correctly.
        mavenGradleRepo.module("org.test", "moduleC").withModuleMetadata().variant("anotherVariantWithFormatCustom", [format: "custom"]).publish()

        when:
        def transitiveSelectedVariant = !gradleMetadataPublished && useIvy()? 'default' : variantToTest
        buildFile << """
            class AddModuleCRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('$transitiveSelectedVariant') {
                        withDependencies {
                            add('org.test:moduleC:1.0')
                        }
                    }
                }
            }

            class AddModuleDRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('anotherVariantWithFormatCustom') {
                        withDependencies {
                            add('org.test:moduleD:1.0')
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleB', AddModuleCRule)
                    //this second rule is here to test that the correct variant was chosen, which is the one adding the dependency to moduleD
                    withModule('org.test:moduleC', AddModuleDRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleC:1.0'() {
                expectGetMetadataMissing()
            }
            'org.test:moduleD:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0") {
                        module('org.test:moduleC:1.0') {
                            module('org.test:moduleD:1.0')
                        }
                    }
                }
            }
        }
    }

    def "resolving one configuration does not influence the result of resolving another configuration."() {
        given:
        repository {
            'org.test:moduleA:1.0'() {
                dependsOn 'org.test:moduleB:1.0'
            }
        }

        when:
        buildFile << """
            class ModifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        withDependencies {
                            //check that the dependency has not been removed already during resolution of the other configuration
                            assert it.size() == 1
                            removeAll { true }
                        }
                    }
                }
            }

            configurations { anotherConfiguration { attributes { attribute(Attribute.of('format', String), 'custom') } } }

            dependencies {
                anotherConfiguration group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }

            dependencies {
                components {
                    withModule('org.test:moduleA', ModifyRule)
                }
            }

            configurations.all {
                incoming.beforeResolve { println "Resolving \$name" }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
            }
        }

        then:
        succeeds 'dependencies'
    }

    def "can make #thing strict"() {
        given:
        repository {
            'org.test:moduleB:1.1' {
                variant 'customVariant', [format: 'custom']
            }
            'org.test:moduleA:1.0'() {
                if (defineAsConstraint) {
                    constraint 'org.test:moduleB:1.1'
                } else {
                    dependsOn 'org.test:moduleB:1.1'
                }
            }
        }

        when:
        buildFile << """
            class ModifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        with${toCamelCase(thing)} { d ->
                            d.findAll { it.name == 'moduleB' }.each {
                                it.version { strictly '1.0' }
                            }
                        }
                    }
                }
            }

            dependencies {
                $variantToTest group: 'org.test', name: 'moduleB', version: '1.1' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}

                components {
                    withModule('org.test:moduleA', ModifyRule)
                }
            }
        """
        if (defineAsConstraint && !gradleMetadataPublished) {
            //in plain ivy, we do not have the constraint published. But we can add still add it.
            buildFile.text = buildFile.text.replace("d ->", "d -> d.add('org.test:moduleB:1.0')")
        }

        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
            }
            'org.test:moduleB:1.1'() {
                expectGetMetadata()
            }
        }

        then:
        fails 'checkDep'
        failure.assertHasCause """Cannot find a version of 'org.test:moduleB' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org.test:moduleB:1.1'
   ${defineAsConstraint? 'Constraint' : 'Dependency'} path ':test:unspecified' --> 'org.test:moduleA:1.0' ($variantToTest) --> 'org.test:moduleB:{strictly 1.0}'"""

        where:
        thing                    | defineAsConstraint
        "dependencies"           | false
        "dependency constraints" | true
    }

    def "can add rejections to #thing"() {
        given:
        repository {
            'org.test:moduleB:1.1' {
                variant 'customVariant', [format: 'custom']
            }
            'org.test:moduleA:1.0'() {
                if (defineAsConstraint) {
                    constraint 'org.test:moduleB:1.+'
                } else {
                    dependsOn 'org.test:moduleB:1.+'
                }
            }
        }

        when:
        buildFile << """
            class ModifyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        with${toCamelCase(thing)} { d ->
                            d.findAll { it.name == 'moduleB' }.each {
                                it.version {
                                    reject '1.1', '1.2'
                                }
                            }
                        }
                    }
                }
            }

            dependencies {
                $variantToTest group: 'org.test', name: 'moduleB', version: '1.1' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}

                components {
                    withModule('org.test:moduleA', ModifyRule)
                }
            }
        """
        if (defineAsConstraint && !gradleMetadataPublished) {
            //in plain ivy, we do not have the constraint published. But we can add still add it.
            buildFile.text = buildFile.text.replace("d ->", "d -> d.add('org.test:moduleB') { version { require '1.+'; reject '1.1', '1.2' }}")
        }

        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
            }
            'org.test:moduleB' {
                expectVersionListing()
                '1.0' {
                    expectGetMetadataMissing()
                }
                '1.1' {
                    expectGetMetadata()
                }
            }
        }

        then:
        fails 'checkDep'
        failure.assertHasCause """Cannot find a version of 'org.test:moduleB' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org.test:moduleB:1.1'
   ${defineAsConstraint? 'Constraint' : 'Dependency'} path ':test:unspecified' --> 'org.test:moduleA:1.0' ($variantToTest) --> 'org.test:moduleB:{require 1.+; reject 1.1 & 1.2}'"""

        where:
        thing                    | defineAsConstraint
        "dependencies"           | false
        "dependency constraints" | true
    }

    def "a rule can provide a custom selection reason thanks to dependency reason"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn group:'org.test', artifact:'moduleB', version:'1.0', reason: 'will be overwritten by rule'
            }
            'org.test:moduleB:1.0'()
        }

        when:
        buildFile << """
            class ReasonRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('$variantToTest') {
                        withDependencies {
                            it.each {
                                it.because 'can set a custom reason in a rule'
                            }
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleA', ReasonRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0") {
                        notRequested()
                        byReason('can set a custom reason in a rule')
                    }
                }
            }
        }

    }

    def "a rule can provide a custom selection reason thanks to dependency constraint reason"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn group:'org.test', artifact:'moduleB', version:'1.0', reason: 'will be overwritten by rule'
                constraint group:'org.test', artifact:'moduleC', version:'1.0', reason: 'will be overwritten by rule'
            }
            'org.test:moduleB:1.0' {
                dependsOn 'org.test:moduleC:1.0'
            }
            'org.test:moduleC:1.0'()
            'org.test:moduleC:1.1'()
        }

        when:
        buildFile << """
            class ReasonRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('$variantToTest') {
                        withDependencies {
                            it.each {
                                it.because 'can set a custom reason in a rule'
                            }
                        }
                        withDependencyConstraints {
                            it.each {
                                it.version { strictly '1.1' }
                                it.because '1.0 is buggy'
                            }
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleA', ReasonRule)
                }
            }
        """
        boolean constraintsUnsupported = !gradleMetadataPublished

        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleC'() {
                '1.0' {
                    if (constraintsUnsupported) {
                        expectGetMetadata()
                        expectGetArtifact()
                    }
                }
                if (!constraintsUnsupported) {
                    '1.1' {
                        expectResolve()
                    }
                }
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        if (constraintsUnsupported) {
            resolve.expectGraph {
                root(':', ':test:') {
                    module("org.test:moduleA:1.0:$expectedVariant") {
                        module("org.test:moduleB:1.0") {
                            notRequested()
                            byReason('can set a custom reason in a rule')
                            module("org.test:moduleC:1.0")
                        }
                    }
                }
            }
        } else {
            resolve.expectGraph {
                root(':', ':test:') {
                    module("org.test:moduleA:1.0:$expectedVariant") {
                        module("org.test:moduleB:1.0") {
                            notRequested()
                            byReason('can set a custom reason in a rule')
                            edge("org.test:moduleC:1.0", "org.test:moduleC:1.1") {
                                notRequested()
                                byAncestor()
                                byConstraint("1.0 is buggy")
                            }
                        }
                        constraint("org.test:moduleC:{strictly 1.1}", "org.test:moduleC:1.1")
                    }
                }
            }
        }
    }
}
