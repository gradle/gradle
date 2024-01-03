/*
 * Copyright 2013 the original author or authors.
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



package org.gradle.integtests.resolve.caching

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.ivy.IvyFileRepository

class CachingDependencyMetadataInMemoryIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def "version list, descriptor and artifact is cached in memory"() {
        given:
        mavenRepo.module("org", "lib").publish()

        file("build.gradle") << """
            configurations {
                one
                two
            }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                one 'org:lib:1.+'
                two 'org:lib:1.+'
            }
            //runs first and resolves
            task resolveOne {
                def files = configurations.one
                doLast {
                    files.files
                }
            }
            //runs second, purges repo
            task purgeRepo(type: Delete, dependsOn: resolveOne) {
                delete "${mavenRepo.uri}"
            }
            //runs last, still works even though local repo is empty
            task resolveTwo(dependsOn: purgeRepo) {
                def files = configurations.two
                doLast {
                    println "Resolved " + files*.name
                }
            }
        """

        when:
        run "resolveTwo"

        then:
        output.contains 'Resolved [lib-1.0.jar]'
    }

    def "descriptors and artifacts are cached across projects and repositories"() {
        given:
        def lib = ivyHttpRepo.module("org", "lib").publish()

        createDirs("impl")
        file("settings.gradle") << "include 'impl'"

        file("build.gradle") << """
            allprojects {
                configurations { conf }
                repositories { ivy { url "${ivyHttpRepo.uri}" } }
                dependencies { conf 'org:lib:1.0' }
                task resolveConf {
                    def files = configurations.conf
                    doLast { println path + " " + files*.name }
                }
            }
            resolveConf.dependsOn(':impl:resolveConf')
        """

        when:
        lib.ivy.expectGet()
        lib.jar.expectGet()
        run "resolveConf"

        then:
        output.contains ':impl:resolveConf [lib-1.0.jar]'
        output.contains ':resolveConf [lib-1.0.jar]'
    }

    def "descriptors and artifacts are separated for different repositories"() {
        given:
        ivyRepo.module("org", "lib").publish()
        def ivyRepo2 = new IvyFileRepository(file("ivy-repo2"))
        ivyRepo2.module("org", "lib", "2.0").publish() //different version of lib

        createDirs("impl")
        file("settings.gradle") << "include 'impl'"

        file("build.gradle") << """
            allprojects {
                configurations {
                    conf {
                        incoming.afterResolve { deps ->
                            println "\${project.path} " + deps.files*.name
                        }
                    }
                }
                dependencies { conf 'org:lib:1.0' }
                task resolveConf {
                    def files = configurations.conf
                    doLast { files.files }
                }
            }
            repositories { ivy { url "${ivyRepo.uri}" } }
            project(":impl") {
                repositories { ivy { url "${ivyRepo2.uri}" } }
                tasks.resolveConf.dependsOn(":resolveConf")
            }
        """

        when:
        runAndFail ":impl:resolveConf"

        then:
        output.contains ': [lib-1.0.jar]'
        //uses different repo that does not contain this dependency
        failure.assertResolutionFailure(":impl:conf").assertHasCause("Could not find org:lib:1.0")
    }

    def "cache expires at the end of build"() {
        given:
        ivyRepo.module("org", "dependency").publish()
        ivyRepo.module("org", "lib").publish()

        file("build.gradle") << """
            configurations { conf }
            repositories { ivy { url "${ivyRepo.uri}" } }
            dependencies { conf 'org:lib:1.0' }
        """

        when:
        run "dependencies", "--configuration", "conf"

        then:
        !output.contains("org:dependency:1.0")

        when:
        ivyRepo.module("org", "lib").dependsOn("org", "dependency", "1.0").publish()
        run "dependencies", "--configuration", "conf"

        then:
        output.contains("org:dependency:1.0")
    }
}
