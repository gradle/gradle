/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsConfigurationReport

class ResolvableConfigurationsReportTaskIntegrationTest extends AbstractIntegrationSpec implements InspectsConfigurationReport {
    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    def "if no configurations present in project, task reports complete absence"() {
        expect:
        succeeds ':resolvableConfigurations'
        reportsCompleteAbsenceOfResolvableConfigurations()
    }

    def "if only consumable configurations present, task reports complete absence"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = false
                assert canBeConsumed
            }
        """

        expect:
        succeeds ':resolvableConfigurations'
        reportsCompleteAbsenceOfResolvableConfigurations()
    }

    def "if only legacy configuration present, and --all not specified, task produces empty report and prompts for rerun"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My legacy configuration"
                assert canBeResolved
                assert canBeConsumed
            }
        """

        expect:
        succeeds ':resolvableConfigurations'
        reportsNoProperConfigurations()
        promptsForRerunToFindMoreConfigurations()
    }

    def "if only legacy configuration present, task reports it if --all flag is set"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My custom legacy configuration"
                assert canBeResolved
                assert canBeConsumed
            }
        """

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':resolvableConfigurations', '--all'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Configuration legacy (l)
--------------------------------------------------
My custom legacy configuration""")

        and:
        hasLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    def "if single resolvable configuration with no attributes or artifacts present, task reports it"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                assert canBeResolved
                canBeConsumed = false
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Configuration custom
--------------------------------------------------
My custom configuration
""")

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    def "if single resolvable configuration present with attributes, task reports it and them"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                assert canBeResolved
                canBeConsumed = false

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Configuration custom
--------------------------------------------------
My custom configuration

Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
""")

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    def "If multiple resolvable configurations present with attributes, task reports them all, sorted alphabetically"() {
        given:
        buildFile << """
            configurations.create("someConf") {
                description = "My first custom configuration"
                assert canBeResolved
                canBeConsumed = false

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }

            configurations.create("otherConf") {
                description = "My second custom configuration"
                assert canBeResolved
                canBeConsumed = false

                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.DOCUMENTATION));
                }
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Configuration otherConf
--------------------------------------------------
My second custom configuration

Attributes
    - org.gradle.category = documentation

--------------------------------------------------
Configuration someConf
--------------------------------------------------
My first custom configuration

Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
""")

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    def "reports resolvable configurations of a Java Library with module dependencies"() {
        given:
        buildFile << """
            plugins { id 'java-library' }

            ${mavenCentralRepository()}

            dependencies {
                api 'org.apache.commons:commons-lang3:3.5'
                implementation 'org.apache.commons:commons-compress:1.19'
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Configuration annotationProcessor
--------------------------------------------------
Annotation processors and their dependencies for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration compileClasspath
--------------------------------------------------
Compile classpath for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api
Extended Configurations
    - compileOnly
    - implementation

--------------------------------------------------
Configuration runtimeClasspath
--------------------------------------------------
Runtime classpath of source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Extended Configurations
    - implementation
    - runtimeOnly

--------------------------------------------------
Configuration testAnnotationProcessor
--------------------------------------------------
Annotation processors and their dependencies for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testCompileClasspath
--------------------------------------------------
Compile classpath for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api
Extended Configurations
    - testCompileOnly
    - testImplementation

--------------------------------------------------
Configuration testRuntimeClasspath
--------------------------------------------------
Runtime classpath of source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Extended Configurations
    - testImplementation
    - testRuntimeOnly
""")

        and:
        doesNotHaveLegacyLegend()
    }

    def "reports resolvable configurations of a Java Library with module dependencies if --all flag is set"() {
        given:
        buildFile << """
            plugins { id 'java-library' }

            ${mavenCentralRepository()}

            configurations {
                archiveLegacy {
                    description = 'Example legacy configuration.'
                    assert canBeConsumed
                    assert canBeResolved
                }
            }

            dependencies {
                api 'org.apache.commons:commons-lang3:3.5'
                implementation 'org.apache.commons:commons-compress:1.19'
            }
        """

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':resolvableConfigurations', '--all'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Configuration annotationProcessor
--------------------------------------------------
Annotation processors and their dependencies for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration archiveLegacy (l)
--------------------------------------------------
Example legacy configuration.

--------------------------------------------------
Configuration compileClasspath
--------------------------------------------------
Compile classpath for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api
Extended Configurations
    - compileOnly
    - implementation

--------------------------------------------------
Configuration runtimeClasspath
--------------------------------------------------
Runtime classpath of source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Extended Configurations
    - implementation
    - runtimeOnly

--------------------------------------------------
Configuration testAnnotationProcessor
--------------------------------------------------
Annotation processors and their dependencies for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testCompileClasspath
--------------------------------------------------
Compile classpath for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api
Extended Configurations
    - testCompileOnly
    - testImplementation

--------------------------------------------------
Configuration testRuntimeClasspath
--------------------------------------------------
Runtime classpath of source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Extended Configurations
    - testImplementation
    - testRuntimeOnly
""")

        and:
        hasLegacyLegend()
    }

    def "specifying a missing config with no configs produces empty report"() {
        expect:
        succeeds ':resolvableConfigurations', '--configuration', 'missing'
        reportsCompleteAbsenceOfResolvableConfigurations()
    }

    def "specifying a missing config produces empty report"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                assert canBeResolved
                canBeConsumed = false
            }
        """

        expect:
        succeeds ':resolvableConfigurations', '--configuration', 'missing'
        outputContains("There are no resolvable configurations on project 'myLib' named 'missing'.")
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    def "specifying a missing config with --all and legacy configs available produces empty report and no suggestion"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                assert canBeResolved
                canBeConsumed = false
            }

            configurations.create("legacy") {
                description = "My custom configuration"
                assert canBeResolved
                assert canBeConsumed
            }
        """

        expect:
        succeeds ':resolvableConfigurations', '--configuration', 'missing', '--all'
        outputContains("There are no resolvable configurations on project 'myLib' named 'missing'.")
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    def "compatibility rules are printed if present"() {
        given: "A compatibility rule applying to the alphabetically first named attribute in the list"
        buildFile << """
            plugins {
                id 'java'
            }

            def flavor = Attribute.of('flavor', String)

            configurations {
                custom {
                    description = "My custom configuration"
                    assert canBeResolved
                    canBeConsumed = false

                    attributes {
                        attribute(flavor, 'vanilla')
                    }
                }
            }

            class CategoryCompatibilityRule implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    details.compatible()
                }
            }

            dependencies.attributesSchema {
                attribute(flavor) {
                    compatibilityRules.add(CategoryCompatibilityRule)
                }
            }
        """.stripIndent()

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Compatibility Rules
--------------------------------------------------
The following Attributes have compatibility rules defined.

    - flavor""")

        and:
        doesNotHaveLegacyLegend()
    }

    def "disambiguation rules are printed if present"() {
        given: "A disambiguation rule applying to the alphabetically first named attribute in the list"
        buildFile << """
            plugins {
                id 'java'
            }

            def flavor = Attribute.of('flavor', String)

            configurations {
                custom {
                    description = "My custom configuration"
                    assert canBeResolved
                    canBeConsumed = false

                    attributes {
                        attribute(flavor, 'vanilla')
                    }
                }
            }

            class CategorySelectionRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    if (details.candidateValues.contains('chocolate')) {
                        details.closestMatch('chocolate')
                    }
                }
            }

            dependencies.attributesSchema {
                attribute(flavor) {
                    disambiguationRules.add(CategorySelectionRule)
                }
            }
        """.stripIndent()

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Disambiguation Rules
--------------------------------------------------
The following Attributes have disambiguation rules defined.

    - flavor""")

        and:
        doesNotHaveLegacyLegend()
    }

    def "disambiguation rules are printed if added to attributes"() {
        given: "A disambiguation rule applying to the alphabetically first named attribute in the list"
        buildFile << """
            plugins {
                id 'java'
            }

            def flavor = Attribute.of('flavor', String)

            configurations {
                custom {
                    description = "My custom configuration"
                    assert canBeResolved
                    canBeConsumed = false

                    attributes {
                        attribute(flavor, 'vanilla')
                    }
                }
            }

            class CategorySelectionRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    if (details.candidateValues.contains('chocolate')) {
                        details.closestMatch('chocolate')
                    }
                }
            }

            dependencies.attributesSchema {
                attribute(flavor) {
                    disambiguationRules.add(CategorySelectionRule)
                }

                def usage = getAttributes().find { it.name == 'org.gradle.usage' }
                setAttributeDisambiguationPrecedence([flavor, usage])
            }
        """.stripIndent()

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Disambiguation Rules
--------------------------------------------------
The following Attributes have disambiguation rules defined.

    - flavor (1)
    - org.gradle.category
    - org.gradle.dependency.bundling
    - org.gradle.jvm.environment
    - org.gradle.jvm.version
    - org.gradle.libraryelements
    - org.gradle.plugin.api-version
    - org.gradle.usage (2)

(#): Attribute disambiguation precedence""")

        and:
        doesNotHaveLegacyLegend()
    }

    def "report prints attribute disambiguation precedence"() {
        given: "A disambiguation rule applying to the alphabetically first named attribute in the list"
        buildFile << """
            plugins {
                id 'java'
            }

            def flavor = Attribute.of('flavor', String)

            configurations {
                custom {
                    description = "My custom configuration"
                    assert canBeResolved
                    canBeConsumed = false

                    attributes {
                        attribute(flavor, 'vanilla')
                    }
                }
            }

            class CategorySelectionRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    if (details.candidateValues.contains('chocolate')) {
                        details.closestMatch('chocolate')
                    }
                }
            }

            dependencies.attributesSchema {
                attribute(flavor) {
                    disambiguationRules.add(CategorySelectionRule)
                }
            }
        """.stripIndent()

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Disambiguation Rules
--------------------------------------------------
The following Attributes have disambiguation rules defined.

    - flavor
    - org.gradle.category (1)
    - org.gradle.dependency.bundling (5)
    - org.gradle.jvm.environment (6)
    - org.gradle.jvm.version (3)
    - org.gradle.libraryelements (4)
    - org.gradle.plugin.api-version
    - org.gradle.usage (2)

(#): Attribute disambiguation precedence""")

        and:
        doesNotHaveLegacyLegend()
    }

    def "specifying --recursive includes transitively extended configurations"() {
        given:
        buildFile << """
            def base = configurations.create("base") {
                description = "Base configuration"
                assert canBeResolved
                canBeConsumed = false
            }

            def mid = configurations.create("mid") {
                description = "Mid configuration"
                assert canBeResolved
                canBeConsumed = false
                extendsFrom base
            }

            def leaf = configurations.create("leaf") {
                description = "Leaf configuration"
                assert canBeResolved
                canBeConsumed = false
                extendsFrom mid
            }
        """

        when:
        succeeds ':resolvableConfigurations', '--recursive'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Base configuration

--------------------------------------------------
Configuration leaf
--------------------------------------------------
Leaf configuration

Extended Configurations
    - base (t)
    - mid

--------------------------------------------------
Configuration mid
--------------------------------------------------
Mid configuration

Extended Configurations
    - base""")

        and:
        hasTransitiveLegend()
    }

    def "not specifying --recursive does not includes transitively extended configurations"() {
        given:
        buildFile << """
            def base = configurations.create("base") {
                description = "Base configuration"
                assert canBeResolved
                canBeConsumed = false
            }

            def mid = configurations.create("mid") {
                description = "Mid configuration"
                assert canBeResolved
                canBeConsumed = false
                extendsFrom base
            }

            def leaf = configurations.create("leaf") {
                description = "Leaf configuration"
                assert canBeResolved
                canBeConsumed = false
                extendsFrom mid
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Base configuration

--------------------------------------------------
Configuration leaf
--------------------------------------------------
Leaf configuration

Extended Configurations
    - mid

--------------------------------------------------
Configuration mid
--------------------------------------------------
Mid configuration

Extended Configurations
    - base""")

        and:
        doesNotHaveTransitiveLegend()
    }

    def "specifying --recursive with no transitively extended configurations does not print legend"() {
        given:
        buildFile << """
            def base = configurations.create("base") {
                description = "Base configuration"
                assert canBeResolved
                canBeConsumed = false
            }

            def mid = configurations.create("mid") {
                description = "Mid configuration"
                assert canBeResolved
                canBeConsumed = false
                extendsFrom base
            }
        """

        when:
        succeeds ':resolvableConfigurations', '--recursive'

        then:
        result.groupedOutput.task(":resolvableConfigurations").assertOutputContains("""--------------------------------------------------
Configuration base
--------------------------------------------------
Base configuration

--------------------------------------------------
Configuration mid
--------------------------------------------------
Mid configuration

Extended Configurations
    - base""")

        and:
        doesNotHaveTransitiveLegend()
    }
}
