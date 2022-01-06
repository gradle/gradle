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

package org.gradle.integtests.resolve.derived

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class LocallyPublishedVariantResolutionTest extends AbstractIntegrationSpec {

    def setup() {
        multiProjectBuild('root', ['consumer']) {
            buildFile << '''

            '''

            file('consumer/build.gradle') << """
                plugins {
                    id 'java'
                }

                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                dependencies {
                    implementation 'org.test:producer:1.0'
                }

                // configurations.runtimeClasspath.resolutionStrategy.assumeFluidDependencies() // no effect on outcome

                abstract class Resolve extends DefaultTask {
                    @InputFiles
                    abstract ConfigurableFileCollection getArtifacts()

                    @Internal
                    List<String> expectations = []

                    @TaskAction
                    void assertThat() {
                        logger.lifecycle 'Found files: {}', artifacts.files*.name
                        assert artifacts.files*.name == expectations
                    }
                }

                tasks.register('resolve', Resolve) {
//                    artifacts.from(configurations.compileClasspath) // exposes producer/build/classes/java/main/
                    artifacts.from(configurations.runtimeClasspath) // exposes the jar
                }

                tasks.register('resolveClasses', Resolve) {
                    artifacts.from(configurations.runtimeClasspath.incoming.artifactView {
                        //withVariantReselection()
                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
                        }
                    }.files)
                }

//                tasks.register('resolveOther', Resolve) {
//                    artifacts.from(configurations.producerArtifacts.incoming.artifactView {
//                        withVariantReselection()
//                        attributes {
//                            attribute(Attribute.of('other', String), 'foobar')
//                        }
//                    }.files)
//                }
            """

            file("consumer/src/main/java/consumer/Multiplier.java").java """
                package consumer;

                public class Multiplier {
                    int multiply(int x, int y) {
                        return x*y;
                    }
                }
            """

        }

            file('producer/settings.gradle').touch()

            file('producer/build.gradle') << """
                plugins {
                    id 'java-library'
                    id 'maven-publish'
                }
                group = 'org.test'
                version = '1.0'

                java {
                    withJavadocJar()
                    withSourcesJar()
                }

                publishing {
                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                }
            """

            file("producer/src/main/java/producer/Adder.java").java """
                package producer;

                public class Adder {
                    int add(int x, int y) {
                        return x+y;
                    }
                }
            """

    }

    def 'consumes all variants of locally published component with GMM'() {
        expect:
        file('consumer/build.gradle') << '''
            resolve {
                expectations = ['producer-1.0.jar']
            }
            resolveClasses {
                expectations = ['main']
            }
        '''
//        succeeds(':consumer:classes', '-i')
        executer.usingProjectDirectory(file('producer'))
        succeeds('publish')

        executer.usingProjectDirectory(null)

//        succeeds(':consumer:resolve', '-i')
        succeeds(':consumer:resolveClasses', '-i')
//        succeeds(':producer:outgoingVariants')

    }

}
