/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
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

    def "artifacts may have no extension"() {
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
        m1.artifact(type: 'jar', ext: '')
        m1.artifact(type: '', ext: '')
        m1.artifact(type: '', ext: '', classifier: 'classy')
        m1.publish()

        buildFile << """
allprojects {
    repositories { ivy { url '$ivyHttpRepo.uri' } }
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
        outputContains("display-names: [test (org:test:1.0), test (org:test:1.0), test-classy (org:test:1.0)]")
        outputContains("ids: [test (org:test:1.0), test (org:test:1.0), test-classy (org:test:1.0)]")
        outputContains("names: [test::jar, test::, test::]")
        outputContains("classifiers: [null, null, classy]")
    }
}
