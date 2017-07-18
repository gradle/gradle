/*
 * Copyright 2017 the original author or authors.
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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Issue

class BeforeResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Issue("gradle/gradle#2480")
    def "can use beforeResolve hook to modify dependency excludes"() {
        def module1 = mavenRepo.module('org.test', 'excluded-dep', '1.0').publish()
        mavenRepo.module('org.test', 'direct-dep', '1.0').dependsOn(module1).publish()

        buildFile << """
repositories {
    maven { url '${mavenRepo.uri}' }
}
configurations {
    conf
}
dependencies {
    conf 'org.test:direct-dep:1.0'
}

configurations.conf.incoming.beforeResolve { resolvableDependencies ->
    resolvableDependencies.dependencies.each { dependency ->
        dependency.exclude module: 'excluded-dep'
    }
}

task printFiles {
    doLast {
        def files = configurations.conf.collect { it.name }
        println files
        assert files == ['direct-dep-1.0.jar']
    }
}

task printFilesWithConfigurationInput {
    dependsOn configurations.conf
    doLast {
        def files = configurations.conf.collect { it.name }
        println files
        assert files == ['direct-dep-1.0.jar']
    }
}

task copyFiles(type:Copy) {
    from configurations.conf
    into 'libs'
}
"""

        when:
        succeeds 'printFiles'

        then: // Succeeds: configuration is not 'resolved' as part of the task inputs
        outputContains('[direct-dep-1.0.jar]')

        when:
        succeeds 'printFilesWithConfigurationInput'

        and:
        succeeds 'copyFiles'

        then: // Currently fails: excluded dependency is copied as part of configuration
        file('libs').assertHasDescendants('direct-dep-1.0.jar')
    }

    @NotYetImplemented
    def "should not fail with the Spring Dependency Management plugin"() {
        given:
        settingsFile << '''
            include 'application', 'library'
        '''
        file('application/build.gradle') << """
        buildscript {
            repositories { jcenter() }
            dependencies { classpath("org.springframework.boot:spring-boot-gradle-plugin:1.5.2.RELEASE") }
        }

        apply plugin: 'java'
        apply plugin: 'eclipse'
        apply plugin: 'org.springframework.boot'
        
        sourceCompatibility = 1.8
        
        repositories { jcenter() }
        
        dependencies {
            compile('org.springframework.boot:spring-boot-starter-actuator')
            compile project(':library')
        }
        
        """

        file('library/build.gradle') << '''
            buildscript {
                repositories { 
                    jcenter()
                }
            }
            
            plugins { id "io.spring.dependency-management" version "1.0.0.RELEASE" }
            
            ext { springBootVersion = '1.5.2.RELEASE' }
            
            apply plugin: 'java\'
            apply plugin: 'eclipse\'
            
            jar {
                baseName = 'gs-multi-module-library\'
                version = '0.0.1-SNAPSHOT\'
            }
            sourceCompatibility = 1.8
            
            repositories {
                jcenter()
            }
            
            dependencies {
                compile('org.springframework.boot:spring-boot-starter')
                testCompile('org.springframework.boot:spring-boot-starter-test')
            }
            
            dependencyManagement {
                imports { mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}") }
            }

'''

        buildFile << '''
        subprojects {
                task resolveDependencies {
                    doLast {
                        println configurations.compile.files
                    }
                }
        }
        '''

        when:
        run 'resolveDependencies'

        then:
        noExceptionThrown()
    }
}
