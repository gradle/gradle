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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest

@FluidDependenciesResolveTest
class ResolvedConfigurationApiIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
rootProject.name = 'test'
"""
        buildFile << """
allprojects {
    configurations {
        compile
        "default" {
            extendsFrom compile
        }
    }
}
"""
    }

    @ToBeFixedForConfigurationCache(because = "task exercises the ResolvedConfiguration API")
    def "artifacts may have no extension"() {
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
        m1.artifact(type: 'jar', ext: '')
        m1.artifact(type: '', ext: '')
        m1.artifact(type: '', ext: '', classifier: 'classy')
        m1.publish()

        buildFile << """
allprojects {
    repositories { ivy { url = '$ivyHttpRepo.uri' } }
}
dependencies {
    compile 'org:test:1.0'
}

task show {
    inputs.files configurations.compile
    doLast {
        println "files: " + configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name }
        println "display-names: " + configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.toString() }
        println "ids: " + configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.id.toString() }
        println "names: " + configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { "\$it.name:\$it.extension:\$it.type" }
        println "classifiers: " + configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.classifier }
    }
}
"""

        when:
        m1.ivy.expectGet()
        // Only request once, both artifacts have the same url and only differ by type. Should probably be the same artifact
        m1.getArtifact(type: '', ext: '').expectGet()
        m1.getArtifact(type: '', ext: '', classifier: 'classy').expectGet()

        run 'show'

        then:
        outputContains("files: [test-1.0, test-1.0, test-1.0-classy]")
        outputContains("display-names: [test-1.0 (org:test:1.0), test-1.0 (org:test:1.0), test-1.0-classy (org:test:1.0)]")
        outputContains("ids: [test-1.0 (org:test:1.0), test-1.0 (org:test:1.0), test-1.0-classy (org:test:1.0)]")
        outputContains("names: [test::jar, test::, test::]")
        outputContains("classifiers: [null, null, classy]")
    }

    @ToBeFixedForConfigurationCache(because = "task exercises the ResolvedConfiguration API")
    def "reports multiple failures to resolve components"() {
        buildFile << """
            repositories { maven { url = '${mavenHttpRepo.uri}' } }
            dependencies {
                compile 'test:test1:1.2'
                compile 'test:test2:1.2'
                compile 'test:test3:1.2'
            }

            task show {
                doLast {
                    configurations.compile.resolvedConfiguration.resolvedArtifacts
                }
            }
"""

        when:
        def m1 = mavenHttpRepo.module("test", "test1", "1.2")
        m1.pom.expectGetMissing()
        def m2 = mavenHttpRepo.module("test", "test2", "1.2")
        m2.pom.expectGetUnauthorized()
        def m3 = mavenHttpRepo.module("test", "test3", "1.2").publish()
        m3.pom.expectGet()

        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show'.")
        failure.assertHasCause("Could not resolve all artifacts for configuration ':compile'.")
        failure.assertHasCause("Could not find test:test1:1.2.")
        failure.assertHasCause("Could not resolve test:test2:1.2.")
    }

    @ToBeFixedForConfigurationCache(because = "task exercises the ResolvedConfiguration API")
    def "reports failure to resolve artifact"() {
        buildFile << """
            repositories { maven { url = '${mavenHttpRepo.uri}' } }
            dependencies {
                compile 'test:test1:1.2'
                compile 'test:test2:1.2'
                compile 'test:test3:1.2'
            }

            task show {
                doLast {
                    configurations.compile.resolvedConfiguration.resolvedArtifacts.each { it.file }
                }
            }
"""

        when:
        def m1 = mavenHttpRepo.module("test", "test1", "1.2").publish()
        m1.pom.expectGet()
        m1.artifact.expectGetMissing()
        def m2 = mavenHttpRepo.module("test", "test2", "1.2").publish()
        m2.pom.expectGet()
        def m3 = mavenHttpRepo.module("test", "test3", "1.2").publish()
        m3.pom.expectGet()

        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show'.")
        failure.assertHasCause("Could not find test1-1.2.jar (test:test1:1.2).")
    }
}
