/*
 * Copyright 2019 the original author or authors.
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

class VariantFilesMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    private Map<String, ?> expectedJavaLibraryAttributes(boolean hasJavaLibraryVariants) {
        if (hasJavaLibraryVariants) {
            ['org.gradle.jvm.version': 8, 'org.gradle.status': useIvy() ? 'integration' : 'release', 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library']
        } else {
            // for ivy, we do not derive any variant attributes
            ['org.gradle.jvm.version': 8, 'org.gradle.status': 'integration']
        }
    }

    def setup() {
        buildFile << """
            class MissingJdk8VariantRule implements ComponentMetadataRule {
                String base
                @javax.inject.Inject
                MissingJdk8VariantRule(String base) {
                    this.base = base
                }
                void execute(ComponentMetadataContext context) {
                    context.details.addVariant('jdk8Runtime', base) {
                        attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8) }
                        withFiles {
                            removeAllFiles()
                            addFile("\${context.details.id.name}-\${context.details.id.version}-jdk8.jar")
                        }
                    }
                }
            }
            class MissingJdk8VariantRuleWithoutBase implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.addVariant('jdk8Runtime') {
                        attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8) }
                        withFiles {
                            addFile("\${context.details.id.name}-\${context.details.id.version}-jdk8.jar")
                        }
                    }
                }
            }

            class MissingFileRule implements ComponentMetadataRule {
                String classifier
                String type
                String url
                @javax.inject.Inject
                MissingFileRule(String classifier, String type, String url) {
                    this.classifier = classifier
                    this.type = type
                    this.url = url
                }
                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('runtime') {
                        withFiles {
                            if (url.empty) {
                                addFile("\${context.details.id.name}-\${context.details.id.version}\${classifier}.\${type}")
                            } else {
                                addFile("\${context.details.id.name}-\${context.details.id.version}\${classifier}.\${type}", url)
                            }
                        }
                    }
                }
            }

            class DirectMetadataAccessVariantRule implements ComponentMetadataRule {
                @javax.inject.Inject
                ObjectFactory getObjects() { }

                void execute(ComponentMetadataContext context) {
                    def id = context.details.id
                    context.details.maybeAddVariant("apiElementsWithMetadata", "api") {
                        attributes {
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, "all-files"))
                        }
                        withFiles {
                            addFile("\${id.name}-\${id.version}.pom")
                            addFile("\${id.name}-\${id.version}.module")
                        }
                    }
                    context.details.maybeAddVariant("compileWithMetadata", "compile") {
                        attributes {
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, "all-files"))
                        }
                        withFiles {
                            addFile("\${id.name}-\${id.version}.pom")
                        }
                    }
                }

            }
        """
    }

    def "missing variant can be added"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule { undeclaredArtifact(classifier: 'jdk8') }
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0'()
        }

        when:
        buildFile << """
            configurations.conf {
                attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8) }
            }
            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    withModule('org.test:moduleA', MissingJdk8VariantRule) { params('runtime') }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'jdk8')
            }
            'org.test:moduleB:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedLibraryAttributes = expectedJavaLibraryAttributes(useMaven() || gradleMetadataPublished)
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    variant('jdk8Runtime', expectedLibraryAttributes)
                    artifact(classifier: 'jdk8')
                    module('org.test:moduleB:1.0')
                }
            }
        }
    }

    def "missing variant can be added without base"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule { undeclaredArtifact(classifier: 'jdk8') }
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0'()
        }

        when:
        buildFile << """
            configurations.conf {
                attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8) }
            }
            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    withModule('org.test:moduleA', MissingJdk8VariantRuleWithoutBase)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'jdk8')
            }
        }

        then:
        succeeds 'checkDep'
        def expectedLibraryAttributes = ['org.gradle.jvm.version': 8, 'org.gradle.status': useIvy() ? 'integration' : 'release'] // the Java library attributes are not transferred
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    variant('jdk8Runtime', expectedLibraryAttributes)
                    artifact(classifier: 'jdk8')
                }
            }
        }
    }

    def "using a non-existing base throws and error"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule { undeclaredArtifact(classifier: 'jdk8') }
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0'()
        }

        when:
        buildFile << """
            configurations.conf {
                attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8) }
            }
            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    withModule('org.test:moduleA', MissingJdk8VariantRule) { params('this-does-not-exist') }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
            }
        }

        then:
        def baseType = useMaven() || gradleMetadataPublished ? 'Variant' : 'Configuration'
        fails 'checkDep'
        failure.assertHasCause "$baseType 'this-does-not-exist' not defined in module org.test:moduleA:1.0"
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "can add variants containing metadata as artifacts using lenient rules"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0'()
        }

        when:
        buildFile << """
            configurations.conf {
                attributes { attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, "all-files")) }
            }
            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    all(DirectMetadataAccessVariantRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectResolve()
            }
            'org.test:moduleB:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDep'
        def allFilesVariant = gradleMetadataPublished ? 'apiElementsWithMetadata' : 'compileWithMetadata'
        def hasModuleFile = gradleMetadataPublished
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    variant(allFilesVariant, ['org.gradle.status': 'release', 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar',
                                            'org.gradle.category': 'documentation', 'org.gradle.docstype': 'all-files'])
                    artifact(type: 'jar')
                    artifact(type: 'pom')
                    if (hasModuleFile) { artifact(type: 'module') }
                    module('org.test:moduleB:1.0') {
                        variant(allFilesVariant, ['org.gradle.status': 'release', 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar',
                                                'org.gradle.category': 'documentation', 'org.gradle.docstype': 'all-files'])
                        artifact(type: 'jar')
                        artifact(type: 'pom')
                        if (hasModuleFile) { artifact(type: 'module') }
                    }
                }
            }
        }
    }

    def "file can be added to existing variant"() {
        def dependencyDeclaration = (useMaven() || gradleMetadataPublished)
            ? "'org.test:moduleA:1.0'" // variant matching
            : "group: 'org.test', name: 'moduleA', version: '1.0', configuration: 'runtime'" // explicit configuration selection for pure ivy

        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule { undeclaredArtifact(classifier: 'extraFeature') }
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0'()
        }

        when:
        buildFile << """
            dependencies {
                conf $dependencyDeclaration
                components {
                    withModule('org.test:moduleA', MissingFileRule) { params('-extraFeature', 'jar', '') }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
                expectGetArtifact(classifier: 'extraFeature')
            }
            'org.test:moduleB:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0:runtime') {
                    artifact()
                    artifact(classifier: 'extraFeature')
                    module('org.test:moduleB:1.0')
                }
            }
        }
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "capabilities of base are preserved"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                variant('special-variant') {
                    attribute('howspecial', 'notso')
                    capability('special-feature')
                    capability('crazy-feature')
                    artifact('special-data')
                }
            }
        }

        when:
        buildFile << """
            class VerySpecialVariantRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    context.details.addVariant('very-special-variant', 'special-variant') {
                        attributes { attribute(Attribute.of('howspecial', String), 'very') }
                    }
                }
            }
            configurations.conf {
                attributes { attribute(Attribute.of('howspecial', String), 'very') }
            }
            dependencies {
                conf('org.test:moduleA:1.0') {
                    capabilities {
                        requireCapability('org.test:special-feature')
                        requireCapability('org.test:crazy-feature')
                    }
                }
                components {
                    withModule('org.test:moduleA', VerySpecialVariantRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'special-data')
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariantAttributes = ['howspecial': 'very', 'org.gradle.status': useIvy() ? 'integration' : 'release']
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    variant('very-special-variant', expectedVariantAttributes)
                    artifact(classifier: 'special-data')
                }
            }
        }
    }

    def "cannot add file with the same name multiple times"() {
        def dependencyDeclaration = (useMaven() || gradleMetadataPublished)
            ? "'org.test:moduleA:1.0'" // variant matching
            : "group: 'org.test', name: 'moduleA', version: '1.0', configuration: 'runtime'" // explicit configuration selection for pure ivy

        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule { undeclaredArtifact(classifier: 'extraFeature') }
            }
        }

        when:
        buildFile << """
            dependencies {
                conf $dependencyDeclaration
                components {
                    withModule('org.test:moduleA', MissingFileRule) { params('-extraFeature', 'jar', '') }
                    withModule('org.test:moduleA', MissingFileRule) { params('-extraFeature', 'jar', "../somewhere/some.jar") }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
            }
        }

        then:
        fails 'checkDep'
        failure.assertHasCause("Cannot add file moduleA-1.0-extraFeature.jar (url: ../somewhere/some.jar) because it is already defined (url: moduleA-1.0-extraFeature.jar)")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    def "can add variants for ivy - #usageAttribute"() {
        // through this, we opt-into variant aware dependency management for a pure ivy module
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0'()
        }

        when:
        buildFile << """
            class IvyVariantDerivation implements ComponentMetadataRule {
                @javax.inject.Inject
                ObjectFactory getObjects() { }

                void execute(ComponentMetadataContext context) {
                    context.details.addVariant('runtimeElements', 'default') { // the way it is published, the ivy 'default' configuration is the runtime variant
                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                        }
                    }
                    context.details.addVariant('apiElements', 'compile') {
                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                        }
                    }
                }
            }
            configurations.conf {
                attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, '$usageAttribute')) }
            }
            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    withModule('org.test:moduleA', IvyVariantDerivation)
                    withModule('org.test:moduleB', IvyVariantDerivation)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectResolve()
            }
            'org.test:moduleB:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariantAttributes = expectedJavaLibraryAttributes(true) + ['org.gradle.usage': usageAttribute]
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    variant(varianName, expectedVariantAttributes)
                    module('org.test:moduleB:1.0') {
                        variant(varianName, expectedVariantAttributes)
                    }
                }
            }
        }

        where:
        usageAttribute | varianName
        'java-api'     | 'apiElements'
        'java-runtime' | 'runtimeElements'
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    def "can add variants for ivy - #usageAttribute - honors conf based excludes "() {
        // through this, we opt-into variant aware dependency management for a pure ivy module
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
                excludeFromConfig 'org.test:moduleD', 'compile'
            }
            'org.test:moduleB:1.0' {
                dependsOn 'org.test:moduleD:1.0'
            }
            'org.test:moduleC:1.0' {
                dependsOn 'org.test:moduleD:1.0'
            }
            'org.test:moduleD:1.0'()
        }

        when:
        buildFile << """
            class IvyVariantDerivation implements ComponentMetadataRule {
                @javax.inject.Inject
                ObjectFactory getObjects() { }

                void execute(ComponentMetadataContext context) {
                    context.details.addVariant('runtimeElements', 'default') { // the way it is published, the ivy 'default' configuration is the runtime variant
                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                        }
                    }
                    context.details.addVariant('apiElements', 'compile') {
                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                        }
                    }
                }
            }
            configurations.conf {
                attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, '$usageAttribute')) }
            }
            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    withModule('org.test:moduleA', IvyVariantDerivation)
                    withModule('org.test:moduleB', IvyVariantDerivation)
                    withModule('org.test:moduleC', IvyVariantDerivation)
                    withModule('org.test:moduleD', IvyVariantDerivation)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectResolve()
            }
            'org.test:moduleB:1.0' {
                expectResolve()
            }
            if (usageAttribute.contains('runtime')) {
                'org.test:moduleD:1.0' {
                    expectResolve()
                }
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariantAttributes = expectedJavaLibraryAttributes(true) + ['org.gradle.usage': usageAttribute]
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    variant(varianName, expectedVariantAttributes)
                    module('org.test:moduleB:1.0') {
                        variant(varianName, expectedVariantAttributes)
                        if (usageAttribute.contains('runtime')) {
                            module('org.test:moduleD:1.0') {
                                variant(varianName, expectedVariantAttributes)
                            }
                        }
                    }
                }
            }
        }

        where:
        usageAttribute | varianName
        'java-api'     | 'apiElements'
        'java-runtime' | 'runtimeElements'
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    def "opts-out of maven artifact discovery when files are added to variant metadata"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule {
                    undeclaredArtifact(classifier: 'extraFeature')
                    undeclaredArtifact(type: 'jar')
                    hasPackaging('jar')
                }
            }
        }

        when:
        buildFile << """
            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    withModule('org.test:moduleA', MissingFileRule) { params('-extraFeature', 'jar', '') }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'extraFeature')
                expectGetArtifact(type: 'jar')
            }
        }

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    artifact(type: 'jar')
                    artifact(classifier: 'extraFeature')
                }
            }
        }
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    def "opts-out of maven artifact discovery when files are added to variant metadata (packaging='notJar')"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule {
                    undeclaredArtifact(classifier: 'extraFeature')
                    undeclaredArtifact(type: 'notJar')
                    hasPackaging('notJar')
                }
            }
        }

        when:
        buildFile << """
            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    withModule('org.test:moduleA', MissingFileRule) { params('-extraFeature', 'jar', '') }
                    // it is not clear if the existing artifact is 'moduleA-1.0.jar' or 'moduleA-1.0.notJar',
                    // because the packaging can indicate to the file extension or not. Since we do not know,
                    // Gradle removes the 'default' artifact as soon as a file rule is applied.
                    // We now have to explicitly add the artifact we expect.
                    withModule('org.test:moduleA', MissingFileRule) { params('', 'notJar', '') }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'extraFeature')
                expectGetArtifact(type: 'notJar')
            }
        }

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    artifact(type: 'notJar')
                    artifact(classifier: 'extraFeature')
                }
            }
        }
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    def "probes for artifact when metadata does not include artifact"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule {
                    undeclaredArtifact(classifier: 'extraFeature')
                    undeclaredArtifact(type: 'notJar')
                    hasPackaging('notJar')
                }
            }
        }

        when:
        buildFile << """
            dependencies {
                conf 'org.test:moduleA:1.0'
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectHeadArtifact(type: 'notJar')
                expectGetArtifact(type: 'notJar')
            }
        }

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    artifact(type: 'notJar')
                }
            }
        }
    }
}
