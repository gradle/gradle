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
import org.gradle.integtests.fixtures.InspectsVariantsReport
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Ignore

class ResolvableVariantsReportTaskIntegrationTest extends AbstractIntegrationSpec implements InspectsVariantsReport {
    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "if no configurations present, resolvable variants task produces empty report"() {
        expect:
        succeeds ':resolvableVariants'
        outputContains('There are no resolvable configurations on project myLib')
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "if only consumable configurations present, resolvable variants task produces empty report"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = false
                canBeConsumed = true
            }
        """

        expect:
        succeeds ':resolvableVariants'
        outputContains('There are no resolvable configurations on project myLib')
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "if only custom configuration present, resolvable variants task reports it"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false
            }
        """

        when:
        succeeds ':resolvableVariants'

        then:
        outputContains """> Task :resolvableVariants
--------------------------------------------------
Configuration custom
--------------------------------------------------
Description = My custom configuration

"""

        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "if only custom configuration present with attributes, resolvable variants task reports it and them"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }
        """

        when:
        succeeds ':resolvableVariants'

        then:
        outputContains """> Task :resolvableVariants
--------------------------------------------------
Configuration custom
--------------------------------------------------
Description = My custom configuration

Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    def "Multiple custom configurations present with attributes, resolvable variants task reports them all"() {
        given:
        buildFile << """
            configurations.create("someConf") {
                description = "My first custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }

            configurations.create("otherConf") {
                description = "My second custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.DOCUMENTATION));
                }
            }
        """

        when:
        succeeds ':resolvableVariants'

        then:
        outputContains """> Task :resolvableVariants
--------------------------------------------------
Configuration otherConf
--------------------------------------------------
Description = My second custom configuration

Attributes
    - org.gradle.category = documentation

--------------------------------------------------
Configuration someConf
--------------------------------------------------
Description = My first custom configuration

Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    @Ignore("This needs to be updated after the behavior of --variant is updated") // TODO: remove ignore
    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "if only custom legacy configuration present, resolvable variants task does not report it"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My custom legacy configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        expect:
        succeeds ':resolvableVariants'
        outputContains('There are no resolvable configurations on project myLib')
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "if only custom legacy configuration present, resolvable variants task reports it if --all flag is set"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My custom legacy configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':resolvableVariants', '--all'

        then:
        outputContains """> Task :resolvableVariants
--------------------------------------------------
Configuration legacy (l)
--------------------------------------------------
Description = My custom legacy configuration

"""

        and:
        hasLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "reports resolvable variants of a Java Library with module dependencies"() {
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
        succeeds ':resolvableVariants'

        then:
        outputContains """> Task :resolvableVariants
--------------------------------------------------
Configuration annotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration compileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api

--------------------------------------------------
Configuration runtimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testAnnotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testCompileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api

--------------------------------------------------
Configuration testRuntimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "reports resolvable variants of a Java Library with module dependencies if --all flag is set"() {
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
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':resolvableVariants', '--all'

        then:
        outputContains """> Task :resolvableVariants
--------------------------------------------------
Configuration annotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration archives (l)
--------------------------------------------------
Description = Configuration for archive artifacts.

--------------------------------------------------
Configuration compileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api

--------------------------------------------------
Configuration default (l)
--------------------------------------------------
Description = Configuration for default artifacts.

--------------------------------------------------
Configuration runtimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testAnnotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testCompileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api

--------------------------------------------------
Configuration testRuntimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        hasLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "specifying a missing config with no configs produces empty report"() {
        expect:
        succeeds ':resolvableVariants', '--configuration', 'missing'
        outputContains("There is no configuration named 'missing' defined on this project.")
        outputContains('There are no resolvable configurations on project myLib')
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableVariants")
    def "specifying a missing config produces empty report"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false
            }
        """

        expect:
        succeeds ':resolvableVariants', '--configuration', 'missing'
        outputContains("There is no configuration named 'missing' defined on this project.")
        outputContains('Here are the available resolvable configurations: custom')
    }
}
