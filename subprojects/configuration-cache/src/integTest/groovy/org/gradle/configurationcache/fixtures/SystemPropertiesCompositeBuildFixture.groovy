/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class SystemPropertiesCompositeBuildFixture {

    static Set<List<BuildWithSystemPropertyDefined>> definitions() {
        Set<List<BuildWithSystemPropertyDefined>> containsIncludedBuildDefinitions = new HashSet()

        Set<List<BuildWithSystemPropertyDefined>> allDefinitions = [
            new RootBuild() as BuildWithSystemPropertyDefined,
            new BuildSrc(),
            new IncludedBuild("included-build")
        ]
            .subsequences()
            .collect { definitions ->
                if (definitions.any { it instanceof IncludedBuild }) {
                    containsIncludedBuildDefinitions.add(new ArrayList(definitions))
                }
                definitions
            }

        containsIncludedBuildDefinitions
            .collect { definitions ->
                definitions.add(new IncludedBuild("included-build-2"))
                definitions
            }

        allDefinitions.addAll(containsIncludedBuildDefinitions)

        return allDefinitions
    }

    static List<Spec> specsWithoutSystemPropertyAccess() {
        specs().findAll { spec -> spec.systemPropertyAccess == SystemPropertyAccess.NO_ACCESS }
    }

    static List<Spec> specsWithSystemPropertyAccess() {
        specs().findAll { spec -> spec.systemPropertyAccess != SystemPropertyAccess.NO_ACCESS }
    }

    static List<Spec> specs() {
        [
            definitions(),
            SystemPropertyAccess.values()
        ]
            .combinations()
            .findAll { List<BuildWithSystemPropertyDefined> definitions, SystemPropertyAccess access ->
                access.isApplicableToBuild(definitions)
            }
            .collect { List<BuildWithSystemPropertyDefined> definitions, SystemPropertyAccess access ->
                new Spec(definitions, access)
            }
    }

    static class Spec {
        private final List<BuildWithSystemPropertyDefined> systemPropertyDefinitions
        private final SystemPropertyAccess systemPropertyAccess

        Spec(List<BuildWithSystemPropertyDefined> systemPropertyDefinitions, SystemPropertyAccess systemPropertyAccess) {
            this.systemPropertyDefinitions = systemPropertyDefinitions
            this.systemPropertyAccess = systemPropertyAccess
        }

        @Override
        String toString() {
            return "definitions: ${systemPropertyDefinitions.join(", ")}, access: $systemPropertyAccess"
        }

        SystemPropertiesCompositeBuildFixture createFixtureFor(AbstractConfigurationCacheIntegrationTest test, String propertyKey) {
            return new SystemPropertiesCompositeBuildFixture(this, test, propertyKey)
        }
    }

    enum SystemPropertyAccess {

        ROOT_BUILD_SCRIPT{

            @Override
            boolean isApplicableToBuild(List<BuildWithSystemPropertyDefined> definitions) {
                return definitions.any { it instanceof RootBuild }
            }

            @Override
            TestFile getAccessLocation(List<BuildWithSystemPropertyDefined> definitions, AbstractIntegrationSpec test) {
                return test.buildFile
            }
        },
        ROOT_SETTINGS_SCRIPT{

            @Override
            boolean isApplicableToBuild(List<BuildWithSystemPropertyDefined> definitions) {
                return definitions.any { it instanceof RootBuild }
            }

            @Override
            TestFile getAccessLocation(List<BuildWithSystemPropertyDefined> definitions, AbstractIntegrationSpec test) {
                return test.file("settings.gradle")
            }
        },
        BUILDSRC_BUILD_SCRIPT{

            @Override
            boolean isApplicableToBuild(List<BuildWithSystemPropertyDefined> definitions) {
                return definitions.any { it instanceof BuildSrc }
            }

            @Override
            TestFile getAccessLocation(List<BuildWithSystemPropertyDefined> definitions, AbstractIntegrationSpec test) {
                return test.file("buildSrc/build.gradle")
            }
        },
        BUILDSRC_SETTINGS_SCRIPT{

            @Override
            boolean isApplicableToBuild(List<BuildWithSystemPropertyDefined> definitions) {
                return definitions.any { it instanceof BuildSrc }
            }

            @Override
            TestFile getAccessLocation(List<BuildWithSystemPropertyDefined> definitions, AbstractIntegrationSpec test) {
                return test.file("buildSrc/settings.gradle")
            }
        },
        INCLUDED_BUILD_SCRIPT{

            @Override
            boolean isApplicableToBuild(List<BuildWithSystemPropertyDefined> definitions) {
                return definitions.any { it instanceof IncludedBuild }
            }

            @Override
            TestFile getAccessLocation(List<BuildWithSystemPropertyDefined> definitions, AbstractIntegrationSpec test) {
                IncludedBuild includedBuild = definitions.find { it instanceof IncludedBuild }
                return test.file("${includedBuild.name}/build.gradle")
            }
        },
        INCLUDED_SETTINGS_SCRIPT{

            @Override
            boolean isApplicableToBuild(List<BuildWithSystemPropertyDefined> definitions) {
                return definitions.any { it instanceof IncludedBuild }
            }

            @Override
            TestFile getAccessLocation(List<BuildWithSystemPropertyDefined> definitions, AbstractIntegrationSpec test) {
                IncludedBuild includedBuild = definitions.find { it instanceof IncludedBuild }
                return test.file("${includedBuild.name}/settings.gradle")
            }
        },
        NO_ACCESS{

            @Override
            boolean isApplicableToBuild(List<BuildWithSystemPropertyDefined> definitions) {
                return true
            }

            @Override
            TestFile getAccessLocation(List<BuildWithSystemPropertyDefined> definitions, AbstractIntegrationSpec test) {
                return null
            }
        }

        abstract boolean isApplicableToBuild(List<BuildWithSystemPropertyDefined> definitions)

        abstract TestFile getAccessLocation(List<BuildWithSystemPropertyDefined> definitions, AbstractIntegrationSpec test)
    }

    static interface BuildWithSystemPropertyDefined {

        void setup(AbstractIntegrationSpec spec, String propertyKey)

        String propertyValue()
    }

    static class RootBuild implements BuildWithSystemPropertyDefined {

        @Override
        void setup(AbstractIntegrationSpec spec, String propertyKey) {
            spec.testDirectory.file("gradle.properties") << "systemProp.$propertyKey=${propertyValue()}"
        }

        @Override
        String propertyValue() {
            return "root build property"
        }

        @Override
        String toString() {
            return "Root build"
        }
    }

    static class IncludedBuild implements BuildWithSystemPropertyDefined {

        final String name

        IncludedBuild(String name) {
            this.name = name
        }

        @Override
        void setup(AbstractIntegrationSpec spec, String propertyKey) {
            spec.testDirectory.file("$name/gradle.properties") << "systemProp.$propertyKey=${propertyValue()}"
            spec.settingsFile("""includeBuild('$name')\n""")
        }

        @Override
        String propertyValue() {
            return "'$name' build property"
        }

        @Override
        String toString() {
            return "'$name' build"
        }
    }

    static class BuildSrc implements BuildWithSystemPropertyDefined {

        @Override
        void setup(AbstractIntegrationSpec spec, String propertyKey) {
            spec.testDirectory.file("buildSrc/gradle.properties") << "systemProp.$propertyKey=${propertyValue()}"
            spec.testDirectory.file("buildSrc/build.gradle").touch()
        }

        @Override
        String propertyValue() {
            return "buildSrc property"
        }

        @Override
        String toString() {
            return "BuildSrc"
        }
    }

    final String task = "echo"
    private final Spec spec
    private final AbstractConfigurationCacheIntegrationTest test
    private final String propertyKey

    SystemPropertiesCompositeBuildFixture(Spec spec, AbstractConfigurationCacheIntegrationTest test, String propertyKey) {
        this.spec = spec
        this.test = test
        this.propertyKey = propertyKey
    }

    void setup() {
        spec.systemPropertyDefinitions.collect {
            it.setup(test, propertyKey)
        }

        test.buildFile << """
            task $task(type: DefaultTask) {
                def property = providers.systemProperty('$propertyKey')
                doFirst {
                    println('Execution: ' + property.orNull)
                }
            }
            """

        TestFile systemPropertyAccessLocation = spec.systemPropertyAccess.getAccessLocation(spec.systemPropertyDefinitions, test)

        if (systemPropertyAccessLocation != null) {
            systemPropertyAccessLocation << "println('Configuration: ' + providers.systemProperty('$propertyKey').orNull)\n"
        }
    }

    String expectedConfigurationTimeValue() {
        String expectedValue
        switch (spec.systemPropertyAccess) {
            case SystemPropertyAccess.ROOT_SETTINGS_SCRIPT:
                RootBuild rootBuild = spec.systemPropertyDefinitions.find { it instanceof RootBuild }
                expectedValue = rootBuild.propertyValue()
                break
            case SystemPropertyAccess.BUILDSRC_SETTINGS_SCRIPT:
                BuildSrc buildSrcBuild = spec.systemPropertyDefinitions.find { it instanceof BuildSrc }
                expectedValue = buildSrcBuild.propertyValue()
                break
            case SystemPropertyAccess.INCLUDED_SETTINGS_SCRIPT:
                IncludedBuild includeBuild = spec.systemPropertyDefinitions.find { it instanceof IncludedBuild }
                expectedValue = includeBuild.propertyValue()
                break
            case SystemPropertyAccess.INCLUDED_BUILD_SCRIPT:
                boolean isBuildSrcDefinition = spec.systemPropertyDefinitions.any { it instanceof BuildSrc }
                boolean isIncludedBuildDefinition = spec.systemPropertyDefinitions.any { it instanceof IncludedBuild }
                expectedValue = isBuildSrcDefinition && isIncludedBuildDefinition
                    ? spec.systemPropertyDefinitions.findAll { it instanceof IncludedBuild }.last()
                    : expectedExecutionTimeValue()
                break
            case SystemPropertyAccess.NO_ACCESS:
                expectedValue = null
                break
            default:
                expectedValue = expectedExecutionTimeValue()
        }

        return expectedValue
    }

    String expectedExecutionTimeValue() {
        BuildWithSystemPropertyDefined buildSrcDefinition =
            spec.systemPropertyDefinitions.find { it instanceof BuildSrc }
        return buildSrcDefinition ? buildSrcDefinition.propertyValue() : spec.systemPropertyDefinitions.last().propertyValue()
    }
}
