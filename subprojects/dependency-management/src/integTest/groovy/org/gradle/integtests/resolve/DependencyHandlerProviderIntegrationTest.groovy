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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

/**
 * Tests covering the use of {@link org.gradle.api.provider.Provider} as an argument in the
 * {@link org.gradle.api.artifacts.dsl.DependencyHandler} block.
 */
class DependencyHandlerProviderIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def "mutating the provider value before it's added resolves to the correct dependency"() {
        when:
        buildFile << """
        configurations { conf }
        
        def lazyDep = objects.property(String).convention("org.mockito:mockito-core:1.8")
        
        dependencies {
            conf lazyDep
        }
        
        lazyDep.set("junit:junit:4.12")
        
        task checkDeps {
            doLast {
                def deps = configurations.conf.incoming.dependencies
                assert deps.find { it instanceof ExternalDependency && it.group == 'junit' && it.name == 'junit' && it.version == '4.12' }
                assert !deps.find { it instanceof ExternalDependency && it.group == 'org.mockito' && it.name == 'mockito-core' && it.version == '1.8'  }
            }
        }
        """
        then:
        succeeds('checkDeps')
    }

    def "mutation after dependencies have been queried provide a reasonable error message"() {
        given:
        buildFile << """
        configurations { conf }
        
        def lazyDep = objects.property(String).convention("org.mockito:mockito-core:1.8")
        
        dependencies {
            conf lazyDep
        }
        // Do the resolve
        configurations.conf.incoming.dependencies
        // Exception do to the property having been finalized
        lazyDep.set("junit:junit:4.12")
        """
        when:
        fails("help")
        then:
        errorOutput.contains("The value for this property cannot be changed any further.")
    }

    def "works correctly with up-to-date checking"() {
        given:
        mavenHttpRepo.module("group", "projectA", "1.1").publish()
        mavenHttpRepo.module("group", "projectA", "1.2").publish()

        when:
        buildFile << """
        repositories {
            maven {
                url = "${mavenRepo.uri}"
            }
        }
        configurations { conf }
        
        dependencies {
            conf provider { "group:projectA:\${property('project.version')}" }
        }
        
        task retrieve (type: Sync) {
            into 'build'
            from configurations.conf
        }
        """
        then:
        // First run
        args('-Pproject.version=1.1')
        succeeds("retrieve").assertTaskExecuted(":retrieve")
        // Second run, task should be 'UP-TO-DATE'
        args('-Pproject.version=1.1')
        succeeds("retrieve").assertTaskSkipped(":retrieve")
        // Third run, new version, should not be 'UP-TO-DATE'
        args('-Pproject.version=1.2')
        succeeds("retrieve").assertTaskExecuted(":retrieve")
    }

    def "property has no value"() {
        when:
        buildFile << """
        configurations { conf }
        
        def emptyDep = objects.property(String)
        
        dependencies {
            conf emptyDep
        }
        // Do the resolve
        configurations.conf.incoming.dependencies
        """
        then:
        fails("help")
        errorOutput.contains("No value has been specified for this property.")
    }

    def "provider throws an exception"() {
        when:
        buildFile << """
        configurations { conf }
        
        def lazyDep = provider {
            throw new GradleException("Boom!")
        }
        
        dependencies {
            conf lazyDep
        }
        // Do the resolve
        configurations.conf.incoming.dependencies
        """
        then:
        fails("help")
        errorOutput.contains("Boom!")
        errorOutput.contains("build.gradle:5")
        errorOutput.contains("build.gradle:12")
    }

    def "reasonable error message when the provider doesn't provide a supported dependency notation"() {
        when:
        buildFile << """
        configurations { conf }
        
        def lazyDep = provider { 
            42
        }
        
        dependencies {
            conf lazyDep
        }
        // Do the resolve
        configurations.conf.incoming.dependencies
        """
        then:
        fails("help")
        errorOutput.contains("Cannot convert the provided notation to an object of type Dependency: 42.")
        errorOutput.contains("The following types/formats are supported:")
    }
}
