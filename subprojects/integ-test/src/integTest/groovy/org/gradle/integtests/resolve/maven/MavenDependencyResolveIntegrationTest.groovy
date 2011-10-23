/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.MavenRepository

class MavenDependencyResolveIntegrationTest extends AbstractIntegrationSpec {
    def "dependency includes main artifact and runtime dependencies of referenced module"() {
        given:
        def module = repo.module("org.gradle", "test", "1.45")
        module.dependsOn("org.gradle", "other", "preview-1")
        module.artifact(classifier: 'classifier')
        module.publish()
        repo.module("org.gradle", "other", "preview-1").publish()

        and:
        buildFile << """
repositories { maven { url "${repo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check << {
    assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']
}
"""

        expect:
        succeeds "check"
    }

    def "dependency that references a classifier includes the matching artifact only plus the runtime dependencies of referenced module"() {
        given:
        def module = repo.module("org.gradle", "test", "1.45")
        module.dependsOn("org.gradle", "other", "preview-1")
        module.artifact(classifier: 'classifier')
        module.artifact(classifier: 'some-other')
        module.publish()
        repo.module("org.gradle", "other", "preview-1").publish()

        and:
        buildFile << """
repositories { maven { url "${repo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45:classifier"
}

task check << {
    assert configurations.compile.collect { it.name } == ['test-1.45-classifier.jar', 'other-preview-1.jar']
}
"""

        expect:
        succeeds "check"
    }

    def "dependency that references an artifact includes the matching artifact only plus the runtime dependencies of referenced module"() {
        given:
        def module = repo.module("org.gradle", "test", "1.45")
        module.dependsOn("org.gradle", "other", "preview-1")
        module.artifact(classifier: 'classifier')
        module.publish()
        repo.module("org.gradle", "other", "preview-1").publish()

        and:
        buildFile << """
repositories { maven { url "${repo.uri}" } }
configurations { compile }
dependencies {
    compile ("org.gradle:test:1.45") {
        artifact {
            name = 'test'
            type = 'jar'
            classifier = 'classifier'
        }
    }
}

task check << {
    assert configurations.compile.collect { it.name } == ['test-1.45-classifier.jar', 'other-preview-1.jar']
}
"""

        expect:
        succeeds "check"
    }

    def getRepo() {
        return new MavenRepository(file("repo"))
    }
}
