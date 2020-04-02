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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Unroll

class DependencyInsightReportVariantDetailsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireOwnGradleUserHomeDir()

        // detector confuses attributes with stack traces
        executer.withStackTraceChecksDisabled()
        new ResolveTestFixture(buildFile).addDefaultVariantDerivationStrategy()
    }

    @Unroll
    def "shows selected variant details"() {
        given:
        settingsFile << "include 'a', 'b', 'c'"
        file('a/build.gradle') << '''
            apply plugin: 'java-library'

            dependencies {
                api project(':b')
                implementation project(':c')
            }
        '''
        ['b', 'c'].each {
            file("${it}/build.gradle") << """
                apply plugin: 'java-library'
            """
        }

        when:
        run "a:dependencyInsight", "--dependency", ":$expectedProject", "--configuration", configuration

        then:
        outputContains """project :$expectedProject
   variant "$expectedVariant" [
      $expectedAttributes
      org.gradle.dependency.bundling = external
      org.gradle.category            = library
      org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

project :$expectedProject
\\--- $configuration"""

        where:
        configuration      | expectedProject | expectedVariant   | expectedAttributes
        'compileClasspath' | 'b'             | 'apiElements'     | 'org.gradle.usage               = java-api\n      org.gradle.libraryelements     = jar (compatible with: classes)'
        'runtimeClasspath' | 'c'             | 'runtimeElements' | 'org.gradle.usage               = java-runtime\n      org.gradle.libraryelements     = jar'
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "shows published variant details"() {
        given:
        mavenRepo.with {
            def leaf = module('org.test', 'leaf', '1.0')
                .withModuleMetadata()
                .variant('api', ['org.gradle.usage': 'java-api', 'org.gradle.category': 'library', 'org.gradle.libraryelements': 'jar', 'org.gradle.test': 'published attribute'])
                .publish()
            module('org.test', 'a', '1.0')
                .dependsOn(leaf)
                .publish()

        }

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org.test:a:1.0'
            }

            configurations.compileClasspath.attributes.attribute(Attribute.of('org.gradle.blah', String), 'something')
        """

        when:
        run "dependencyInsight", "--dependency", "leaf"

        then:
        outputContains """org.test:leaf:1.0
   variant "api" [
      org.gradle.usage               = java-api
      org.gradle.category            = library
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.test                = published attribute (not requested)
      org.gradle.status              = release (not requested)

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.blah                = something
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

org.test:leaf:1.0
\\--- org.test:a:1.0
     \\--- compileClasspath
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "Asking for variant details of 'FAILED' modules doesn't break the report"() {
        given:
        mavenRepo.module("org", "top").dependsOnModules("middle").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains """
org:middle:1.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    def "shows the target configuration name as variant display name for external dependencies which are not variant-aware"() {
        given:
        def leaf = mavenRepo.module('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "top").dependsOn(leaf).publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains """
org:leaf:1.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:leaf:1.0
\\--- org:top:1.0
     \\--- conf
"""
    }

    def "shows missing attributes when the target variant doesn't have any of its own"() {
        given:
        def leaf = mavenRepo.module('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "top").dependsOn(leaf).publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    attributes.attribute(Attribute.of('usage', String), 'dummy')
                }
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains """org:leaf:1.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
      Requested attributes not found in the selected variant:
         usage                      = dummy
   ]

org:leaf:1.0
\\--- org:top:1.0
     \\--- conf
"""
    }

    def "correctly reports attributes declared on dependencies"() {
        given:
        mavenRepo.module('org', 'testA', '1.0').publish()
        mavenRepo.module('org', 'testB', '1.0').publish()

        buildFile << """
            def CUSTOM_ATTRIBUTE = Attribute.of('custom', CustomAttributeType)
            dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)
            def configValue = objects.named(CustomAttributeType.class, 'conf_value')
            def dependencyValue = objects.named(CustomAttributeType.class, 'dep_value')

            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    attributes.attribute(CUSTOM_ATTRIBUTE, configValue)
                }
            }
            dependencies {
                components {
                    all {
                        attributes {
                            attribute(CUSTOM_ATTRIBUTE, dependencyValue)
                        }
                    }
                }
                conf('org:testA:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, dependencyValue)
                    }
                }
                conf('org:testB:+') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, dependencyValue)
                    }
                }
            }

            interface CustomAttributeType extends Named {}
        """

        when:
        run 'dependencyInsight', "--dependency", "test", "--configuration", "conf"

        then:
        outputContains """
org:testA:1.0
   variant "runtime" [
      custom                     = dep_value
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:testA:1.0
\\--- conf

org:testB:1.0
   variant "runtime" [
      custom                     = dep_value
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:testB:+ -> 1.0
\\--- conf
"""

    }


}
