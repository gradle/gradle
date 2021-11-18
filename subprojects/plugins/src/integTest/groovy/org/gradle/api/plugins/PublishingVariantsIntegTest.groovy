/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure

import static org.gradle.util.Matchers.containsText

class PublishingVariantsIntegTest extends AbstractIntegrationSpec {
    def "publishing variants with duplicate names fails"() {
        given:
        settingsFile << "rootProject.name = 'lib'"

        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            repositories {
                mavenLocal()
            }

            //def conf1 = configurations.create('sample1')
            //def conf2 = configurations.create('sample1')

            //components.java.withVariantsFromConfiguration(conf1) {
            //    skip()
            //}
            //components.java.withVariantsFromConfiguration(conf2)

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            group 'org.sample.SampleLib'
            """.stripIndent()

        file("src/main/java/org/sample/SampleLib.java") << """
            public class SampleLib {
                public void foo() {}
            }
            """.stripIndent()

        when:
        succeeds("outgoingVariants", "publishToMavenLocal")

        then:
        outputContains("Variant")
    }

    def "test variant guarantees"() {
        given:
        settingsFile << "rootProject.name = 'lib'"

        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            repositories {
                mavenLocal()
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            group 'org.sample.SampleLib'
            """.stripIndent()

        file("src/main/java/org/sample/SampleLib.java") << """
            public class SampleLib {
                public void foo() {}
            }
            """.stripIndent()

        expect:
        succeeds("build", "publishToMavenLocal")
    }

    def "variants are not publishable when using non-publishable attribute: #attributeValue"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            def testConf = configurations.create('testConf') { Configuration cnf ->
                cnf.canBeConsumed = true
                cnf.canBeResolved = false
                cnf.attributes {
                    attribute($interfaceType.$attributeConstant, objects.named($interfaceType, $interfaceType.$attributeValueConstant))
                }
            }

            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testConf) {
                it.mapToMavenScope("runtime")
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }""".stripIndent()

        expect:
        ExecutionFailure failure = fails("publishToMavenLocal")
        failure.assertHasCause("Cannot publish feature variant 'testConf' as it is defined by unpublishable attributes: '$attributeValue'")

        where:
        interfaceType || attributeConstant || attributeValueConstant || attributeValue
        'Sources' || 'SOURCES_ATTRIBUTE' || 'ALL_SOURCE_DIRS' || 'org.gradle.sources'
//        'attribute(Sources.SOURCES_ATTRIBUTE, objects.named(Sources, Sources.ALL_SOURCE_DIRS))' ||
//        'attribute(TestType.TEST_TYPE_ATTRIBUTE, objects.named(TestType, TestType.UNIT_TESTS))' || 'org.gradle.testsuitetype'
//        'attribute(TestType.TEST_TYPE_ATTRIBUTE, objects.named(TestType, TestType.INTEGRATION_TESTS))' || 'org.gradle.testsuitetype'
//        'attribute(Verification.SOURCES_ATTRIBUTE, objects.named(Sources, Sources.ALL_SOURCE_DIRS))' || 'org.gradle.sources'
//        'attribute(Sources.SOURCES_ATTRIBUTE, objects.named(Sources, Sources.ALL_SOURCE_DIRS))' || 'org.gradle.sources'
    }
}
