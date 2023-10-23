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

package org.gradle.api.publish.maven

import org.gradle.api.publish.maven.internal.publication.MavenComponentParser
import org.gradle.test.fixtures.maven.MavenJavaModule

class MavenPublishJavaIntegTest extends AbstractMavenPublishJavaIntegTest {

    boolean withDocs() {
        false
    }

    List<String> features() {
        [MavenJavaModule.MAIN_FEATURE]
    }

    def "can publish java-library without warning when dependency with maven incompatible version and using versionMapping"() {
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:1.+"
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        versionMapping {
                            allVariants {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputDoesNotContain(MavenComponentParser.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["runtime"] as Set
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:1.0")

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("commons-collections:commons-collections:1.0") {
                rejects()
                noMoreExcludes()
            }
            noMoreDependencies()
        }
    }

    def "a component's variant can be modified before publishing"() {
        given:
        createBuildScripts """
            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }
            dependencies {
                api 'org:foo:1.0'
                implementation 'org:bar:1.0'
            }
            components.java.withVariantsFromConfiguration(configurations.runtimeElements) {
                skip()
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        with(javaLibrary.parsedPom) {
            assert scopes.size() == 1
            scopes['compile'].expectDependency('org:foo:1.0')
        }
        with(javaLibrary.parsedModuleMetadata) {
            assert variants.size() == 1
            assert variants[0].name == "apiElements"
            assert variants[0].dependencies*.coords == ["org:foo:1.0"]
        }
    }

    def "can ignore all publication warnings by variant name"() {
        given:
        def silenceMethod = "suppressPomMetadataWarningsFor"
        createBuildScripts("""

            configurations.apiElements.outgoing.capability 'org:foo:1.0'
            configurations.runtimeElements.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        $silenceMethod('runtimeElements')
                        $silenceMethod('apiElements')
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputDoesNotContain(MavenComponentParser.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublished()
    }

    def "can not publish variant with attribute specifying category = verification"() {
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            def testConf = configurations.create('testConf') {
                assert canBeResolved
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
            }

            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testConf) {
                it.mapToMavenScope("runtime")
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)


        expect:
        fails('publish')
        failure.assertHasCause("Cannot publish module metadata for component 'java' which would include a variant 'testConf' that contains a 'org.gradle.category' attribute with a value of 'verification'.  " +
            "This attribute is reserved for test verification output and is not publishable.  " + variantAttributesLink())
    }

    def variantAttributesLink() {
        documentationRegistry.getDocumentationRecommendationFor("on this", "variant_attributes", "sec:verification_category")
    }

    def "can not publish variant with attribute specifying category = verification if defining new attribute with string"() {
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            def testConf = configurations.create('testConf') {
                assert canBeResolved
                attributes.attribute(Attribute.of('org.gradle.category', String), 'verification')
            }

            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testConf) {
                it.mapToMavenScope("runtime")
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        expect:
        fails('publish')
        failure.assertHasCause("Cannot publish module metadata for component 'java' which would include a variant 'testConf' that contains a 'org.gradle.category' attribute with a value of 'verification'.  " +
            "This attribute is reserved for test verification output and is not publishable.  " + variantAttributesLink())
    }

    def "can not publish test results from java test suite"() {
        given:
        createBuildScripts("""
            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnit()
                    }
                }
            }

            def testResultsElementsForTest = configurations.testResultsElementsForTest
            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testResultsElementsForTest) {
                it.mapToMavenScope("runtime")
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        file("src/test/java/com/example/SampleTest.java") << """
            package com.example;

            import org.junit.Test;

            public class SampleTest {
                @Test
                public void checkSomething() {
                    // pass
                }
            }""".stripIndent()

        expect:
        fails('test', 'publish')
        failure.assertHasCause("Cannot publish module metadata for component 'java' which would include a variant 'testResultsElementsForTest' that contains a 'org.gradle.category' attribute with a value of 'verification'.  " +
            "This attribute is reserved for test verification output and is not publishable.  " + variantAttributesLink())
    }

    def "can publish variants with attribute specifying category if not verification"() {
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            def testConf = configurations.create('testConf') {
                assert canBeResolved
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, 'not verification'))
            }

            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testConf) {
                it.mapToMavenScope("runtime")
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        expect:
        succeeds('publish')
    }
}
