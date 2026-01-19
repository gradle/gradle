/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.api.internal.artifacts.repositories.distribution.DefaultAvailableDistributionModules
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.installation.GradleInstallation
import org.gradle.util.internal.VersionNumber

/**
 * Tests {@link org.gradle.api.artifacts.dsl.RepositoryHandler#gradleDistribution()}.
 */
class GradleDistributionRepositoryResolveIntegrationTest extends AbstractIntegrationSpec {

    private static final GROOVY_VERSION = GroovySystem.version

    /**
     * Documents/controls the transitive classpath resolved for any given dependency
     * when resolved alone in a dependency graph. This documents user-facing behavior.
     * If entries are removed from this map, or if dependencies are added or removed from
     * the classpath value for any given entry, user-facing behavior has changed.
     */
    private static final Map<String, List<? extends CharSequence>> EXPECTED_CLASSPATHS = [
        ("org.apache.groovy:groovy-nio:${GROOVY_VERSION}".toString()) : ["groovy-nio-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar"],
        ("org.apache.groovy:groovy-docgenerator:${GROOVY_VERSION}".toString()) : ["groovy-docgenerator-${GROOVY_VERSION}.jar", "groovy-templates-${GROOVY_VERSION}.jar", "groovy-xml-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar", "qdox-1.12.1.jar"],
        ("org.apache.ant:ant-launcher:1.10.15") : ["ant-launcher-1.10.15.jar"],
        ("org.apache.ant:ant:1.10.15") : ["ant-1.10.15.jar", "ant-launcher-1.10.15.jar"],
        ("com.thoughtworks.qdox:qdox:1.12.1") : ["qdox-1.12.1.jar"],
        ("com.github.javaparser:javaparser-core:3.27.1") : ["javaparser-core-3.27.1.jar"],
        ("org.apache.groovy:groovy-json:${GROOVY_VERSION}".toString()) : ["groovy-json-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar"],
        ("org.apache.groovy:groovy-groovydoc:${GROOVY_VERSION}".toString()) : ["groovy-groovydoc-${GROOVY_VERSION}.jar", "groovy-docgenerator-${GROOVY_VERSION}.jar", "groovy-templates-${GROOVY_VERSION}.jar", "groovy-xml-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar", "javaparser-core-3.27.1.jar", "qdox-1.12.1.jar"],
        ("org.apache.groovy:groovy-datetime:${GROOVY_VERSION}".toString()) : ["groovy-datetime-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar"],
        ("org.apache.groovy:groovy-xml:${GROOVY_VERSION}".toString()) : ["groovy-xml-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar"],
        ("org.apache.groovy:groovy-astbuilder:${GROOVY_VERSION}".toString()) : ["groovy-astbuilder-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar"],
        ("org.apache.ant:ant-antlr:1.10.15") : ["ant-antlr-1.10.15.jar"],
        ("org.apache.groovy:groovy:${GROOVY_VERSION}".toString()) : ["groovy-${GROOVY_VERSION}.jar"],
        ("org.apache.groovy:groovy-templates:${GROOVY_VERSION}".toString()) : ["groovy-templates-${GROOVY_VERSION}.jar", "groovy-xml-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar"],
        ("org.apache.groovy:groovy-dateutil:${GROOVY_VERSION}".toString()) : ["groovy-dateutil-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar"],
        ("org.apache.groovy:groovy-ant:${GROOVY_VERSION}".toString()) : ["groovy-ant-${GROOVY_VERSION}.jar", "ant-1.10.15.jar", "ant-antlr-1.10.15.jar", "ant-launcher-1.10.15.jar", "groovy-groovydoc-${GROOVY_VERSION}.jar", "groovy-docgenerator-${GROOVY_VERSION}.jar", "groovy-templates-${GROOVY_VERSION}.jar", "groovy-xml-${GROOVY_VERSION}.jar", "groovy-${GROOVY_VERSION}.jar", "javaparser-core-3.27.1.jar", "qdox-1.12.1.jar"],
    ]

    def "can resolve all exposed modules"() {
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("${dependencyNotation}")
            }

            ${resolveTask}
        """

        when:
        succeeds("resolve")

        then:
        outputContains(EXPECTED_CLASSPATHS[dependencyNotation].toString())

        where:
        dependencyNotation << getExposedModules()
    }

    def "attributes are attached to resolved variant"() {
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org.apache.groovy:groovy:$GROOVY_VERSION")
            }
        """

        when:
        succeeds("dependencyInsight", "--configuration", "conf", "--dependency", "groovy")

        then:
        outputContains("""org.apache.groovy:groovy:$GROOVY_VERSION
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.usage           | java-runtime |           |""")
    }

    def "fails to resolve variant if incompatible attributes are requested"() {
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf {
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, named(Usage, Usage.JAVA_API))
                    }
                }
            }

            dependencies {
                conf("org.apache.groovy:groovy:$GROOVY_VERSION")
            }

            ${resolveTask}
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("""No matching variant of org.apache.groovy:groovy:$GROOVY_VERSION was found. The consumer was configured to find attribute 'org.gradle.usage' with value 'java-api' but:
  - Variant 'runtime':
      - Incompatible because this component declares attribute 'org.gradle.usage' with value 'java-runtime' and the consumer needed attribute 'org.gradle.usage' with value 'java-api'""")
    }

    def "fails to resolve dependency from gradle distribution using different version than is present"() {
        int major = VersionNumber.parse(GROOVY_VERSION).major
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org.apache.groovy:groovy:${major}.0.0")
            }

            ${resolveTask}
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Could not find org.apache.groovy:groovy:4.0.0")
    }

    def "can resolve dynamic versioned dependencies from the gradle distribution"() {
        int major = VersionNumber.parse(GROOVY_VERSION).major
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org.apache.groovy:groovy:${major}+")
            }

            ${resolveTask}
        """

        when:
        succeeds("resolve")

        then:
        outputContains("[groovy-${GROOVY_VERSION}.jar]")
    }

    def "fails to resolve dynamic version from distribution when requested version does not match version in distribution"() {
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org.apache.groovy:groovy:3+")
            }

            ${resolveTask}
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("""Could not find any version that matches org.apache.groovy:groovy:3+.
Versions that do not match: $GROOVY_VERSION""")
    }

    def "fails to resolve explicit artifacts from distribution repository"() {
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org.apache.groovy:groovy:$GROOVY_VERSION:cls")
            }

            ${resolveTask}
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Could not resolve org.apache.groovy:groovy:$GROOVY_VERSION")
        failure.assertHasCause("Cannot request explicit artifact from distribution repository.")
    }

    def "can fallback to later repositories if dependency not found in distribution repository"() {
        mavenRepo.module("org", "foo").publish()

        buildFile << """
            ${gradleDistributionRepository()}
            ${mavenTestRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org:foo:1.0")
            }

            ${resolveTask}
        """

        when:
        succeeds("resolve")

        then:
        outputContains("[foo-1.0.jar]")
    }

    def "dependencies resolved from distribution repository can enter capability conflicts"() {
        mavenRepo.module("org", "foo")
            .withoutDefaultVariants()
            .withVariant("runtime") {
                capability("org", "foo", "1.0")
                capability("org.apache.groovy", "groovy", "4.0.0")
            }
            .withModuleMetadata()
            .publish()


        buildFile << """
            ${gradleDistributionRepository()}
            ${mavenTestRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org:foo:1.0")
                conf("org.apache.groovy:groovy:$GROOVY_VERSION")
            }

            ${resolveTask}
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("""Module 'org:foo' has been rejected:
   Cannot select module with conflict on capability 'org.apache.groovy:groovy:4.0.0' also provided by ['org.apache.groovy:groovy:$GROOVY_VERSION' (runtime)]""")
        failure.assertHasCause("""Module 'org.apache.groovy:groovy' has been rejected:
   Cannot select module with conflict on capability 'org.apache.groovy:groovy:$GROOVY_VERSION' also provided by ['org:foo:1.0' (runtime)]""")
    }

    String gradleDistributionRepository() {
        """
            repositories {
                gradleDistribution()
            }
        """
    }

    String getResolveTask(String confName = "conf") {
        """
            tasks.register("resolve") {
                def files = configurations.${confName}.incoming.files
                doLast {
                    println(files*.name)
                }
            }
        """
    }

    List<String> getExposedModules() {
        GradleInstallation testInstallation = new GradleInstallation(buildContext.gradleHomeDir)
        ModuleRegistry moduleRegistry = new DefaultModuleRegistry(testInstallation)
        DefaultAvailableDistributionModules underTest = new DefaultAvailableDistributionModules(moduleRegistry)
        return underTest.getAvailableModules().collect { it.getDisplayName() }
    }

}
