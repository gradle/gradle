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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class OutgoingVariantsReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    def "reports outgoing variants of a Java Library"() {
        buildFile << """
            plugins { id 'java-library' }
        """

        when:
        run ':outgoingVariants'

        then:
        outputContains """> Task :outgoingVariants
--------------------------------------------------
Variant apiElements
--------------------------------------------------
Description = API elements for main.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api

Artifacts
    - jar at build/libs/myLib.jar

Secondary variants (*)
    - Variant : classes
       - Attributes
          - org.gradle.category            = library
          - org.gradle.dependency.bundling = external
          - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
          - org.gradle.libraryelements     = classes
          - org.gradle.usage               = java-api
       - Artifacts
          - java-classes-directory at build/classes/java/main

--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Description = Elements of runtime for main.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

Artifacts
    - jar at build/libs/myLib.jar

Secondary variants (*)
    - Variant : classes
       - Attributes
          - org.gradle.category            = library
          - org.gradle.dependency.bundling = external
          - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
          - org.gradle.libraryelements     = classes
          - org.gradle.usage               = java-runtime
       - Artifacts
          - java-classes-directory at build/classes/java/main
    - Variant : resources
       - Attributes
          - org.gradle.category            = library
          - org.gradle.dependency.bundling = external
          - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
          - org.gradle.libraryelements     = resources
          - org.gradle.usage               = java-runtime
       - Artifacts
          - java-resources-directory at build/resources/main
"""
        and:
        doesNotHaveLegacyVariantsLegend()
        hasSecondaryVariantsLegend()
    }

    def "reports a single outgoing variant of a Java Library"() {
        buildFile << """
            plugins { id 'java-library' }
        """

        when:
        run ':outgoingVariants', '--variant', 'runtimeElements'

        then:
        outputContains """> Task :outgoingVariants
--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Description = Elements of runtime for main.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

Artifacts
    - jar at build/libs/myLib.jar

Secondary variants (*)
    - Variant : classes
       - Attributes
          - org.gradle.category            = library
          - org.gradle.dependency.bundling = external
          - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
          - org.gradle.libraryelements     = classes
          - org.gradle.usage               = java-runtime
       - Artifacts
          - java-classes-directory at build/classes/java/main
    - Variant : resources
       - Attributes
          - org.gradle.category            = library
          - org.gradle.dependency.bundling = external
          - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
          - org.gradle.libraryelements     = resources
          - org.gradle.usage               = java-runtime
       - Artifacts
          - java-resources-directory at build/resources/main
"""

        and:
        doesNotHaveLegacyVariantsLegend()
        hasSecondaryVariantsLegend()
    }

    def "lists all variants when using a wrong variant name"() {
        buildFile << """
            plugins { id 'java-library' }
        """

        when:
        run ':outgoingVariants', '--variant', 'nope'

        then:
        outputContains("""> Task :outgoingVariants
There is no variant named 'nope' defined on this project.
Here are the available outgoing variants: apiElements, archives, compile, compileOnly, default, runtime, runtimeElements, testCompile, testCompileOnly, testRuntime
""")
        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveSecondaryVariantsLegend()

    }

    def "can show all variants"() {
        buildFile << """
            plugins { id 'java-library' }
        """

        when:
        executer.expectDeprecationWarning()
        run ':outgoingVariants', '--all'

        then:
        outputContains """> Task :outgoingVariants
--------------------------------------------------
Variant apiElements
--------------------------------------------------
Description = API elements for main.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api

Artifacts
    - jar at build/libs/myLib.jar

Secondary variants (*)
    - Variant : classes
       - Attributes
          - org.gradle.category            = library
          - org.gradle.dependency.bundling = external
          - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
          - org.gradle.libraryelements     = classes
          - org.gradle.usage               = java-api
       - Artifacts
          - java-classes-directory at build/classes/java/main

--------------------------------------------------
Variant archives (l)
--------------------------------------------------
Description = Configuration for archive artifacts.

Artifacts
    - jar at build/libs/myLib.jar

--------------------------------------------------
Variant compile (l)
--------------------------------------------------
Description = Dependencies for source set 'main' (deprecated, use 'implementation' instead).

--------------------------------------------------
Variant compileOnly (l)
--------------------------------------------------
Description = Compile only dependencies for source set 'main'.

--------------------------------------------------
Variant default (l)
--------------------------------------------------
Description = Configuration for default artifacts.

Artifacts
    - jar at build/libs/myLib.jar

--------------------------------------------------
Variant runtime (l)
--------------------------------------------------
Description = Runtime dependencies for source set 'main' (deprecated, use 'runtimeOnly' instead).

Artifacts
    - jar at build/libs/myLib.jar

--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Description = Elements of runtime for main.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

Artifacts
    - jar at build/libs/myLib.jar

Secondary variants (*)
    - Variant : classes
       - Attributes
          - org.gradle.category            = library
          - org.gradle.dependency.bundling = external
          - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
          - org.gradle.libraryelements     = classes
          - org.gradle.usage               = java-runtime
       - Artifacts
          - java-classes-directory at build/classes/java/main
    - Variant : resources
       - Attributes
          - org.gradle.category            = library
          - org.gradle.dependency.bundling = external
          - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
          - org.gradle.libraryelements     = resources
          - org.gradle.usage               = java-runtime
       - Artifacts
          - java-resources-directory at build/resources/main

--------------------------------------------------
Variant testCompile (l)
--------------------------------------------------
Description = Dependencies for source set 'test' (deprecated, use 'testImplementation' instead).

--------------------------------------------------
Variant testCompileOnly (l)
--------------------------------------------------
Description = Compile only dependencies for source set 'test'.

--------------------------------------------------
Variant testRuntime (l)
--------------------------------------------------
Description = Runtime dependencies for source set 'test' (deprecated, use 'testRuntimeOnly' instead).

Artifacts
    - jar at build/libs/myLib.jar
"""

        and:
        hasLegacyVariantsLegend()
        hasSecondaryVariantsLegend()
    }

    def "can show a legacy variant only"() {
        buildFile << """
            plugins { id 'java-library' }
        """

        when:
        executer.expectDeprecationWarning()
        run ':outgoingVariants', '--variant', 'compile'

        then:
        outputContains """> Task :outgoingVariants
--------------------------------------------------
Variant compile (l)
--------------------------------------------------
Description = Dependencies for source set 'main' (deprecated, use 'implementation' instead).
"""

        and:
        hasLegacyVariantsLegend()
        doesNotHaveSecondaryVariantsLegend()
    }

    private void hasSecondaryVariantsLegend() {
        outputContains("(*) Secondary variants are variants created via the Configuration#getOutgoing(): ConfigurationPublications API which also participate in selection, in addition to the configuration itself.")
    }

    private void doesNotHaveSecondaryVariantsLegend() {
        outputDoesNotContain("(*) Secondary variants are variants created via the Configuration#getOutgoing(): ConfigurationPublications API which also participate in selection, in addition to the configuration itself.")
    }

    private void hasLegacyVariantsLegend() {
        outputContains("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.")
    }

    private void doesNotHaveLegacyVariantsLegend() {
        outputDoesNotContain("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.")
    }
}
