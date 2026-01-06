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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.VersionNumber

/**
 * Tests {@link org.gradle.api.artifacts.dsl.RepositoryHandler#gradleDistribution()}.
 */
class GradleDistributionRepositoryResolveIntegrationTest extends AbstractIntegrationSpec {

    def "can resolve dependency from the gradle distribution"() {
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org.apache.groovy:groovy:\${GroovySystem.version}")
            }

            ${resolveTask}
        """

        when:
        succeeds("resolve")

        then:
        outputContains("[groovy-${GroovySystem.version}.jar]")
    }

    def "attributes are attached to resolved variant"() {
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org.apache.groovy:groovy:\${GroovySystem.version}")
            }
        """

        when:
        succeeds("dependencyInsight", "--configuration", "conf", "--dependency", "groovy")

        then:
        outputContains("""org.apache.groovy:groovy:${GroovySystem.version}
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
                conf("org.apache.groovy:groovy:\${GroovySystem.version}")
            }

            ${resolveTask}
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("""No matching variant of org.apache.groovy:groovy:${GroovySystem.version} was found. The consumer was configured to find attribute 'org.gradle.usage' with value 'java-api' but:
  - Variant 'runtime':
      - Incompatible because this component declares attribute 'org.gradle.usage' with value 'java-runtime' and the consumer needed attribute 'org.gradle.usage' with value 'java-api'""")
    }

    def "fails to resolve dependency from gradle distribution using different version than is present"() {
        int major = VersionNumber.parse(GroovySystem.version).major
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
        int major = VersionNumber.parse(GroovySystem.version).major
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
        outputContains("[groovy-${GroovySystem.version}.jar]")
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
Versions that do not match: ${GroovySystem.version}""")
    }

    def "fails to resolve explicit artifacts from distribution repository"() {
        buildFile << """
            ${gradleDistributionRepository()}

            configurations {
                conf
            }

            dependencies {
                conf("org.apache.groovy:groovy:\${GroovySystem.version}:cls")
            }

            ${resolveTask}
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Could not resolve org.apache.groovy:groovy:${GroovySystem.version}")
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
                conf("org.apache.groovy:groovy:\${GroovySystem.version}")
            }

            ${resolveTask}
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("""Module 'org:foo' has been rejected:
   Cannot select module with conflict on capability 'org.apache.groovy:groovy:4.0.0' also provided by ['org.apache.groovy:groovy:${GroovySystem.version}' (runtime)]""")
        failure.assertHasCause("""Module 'org.apache.groovy:groovy' has been rejected:
   Cannot select module with conflict on capability 'org.apache.groovy:groovy:${GroovySystem.version}' also provided by ['org:foo:1.0' (runtime)]""")
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

}
