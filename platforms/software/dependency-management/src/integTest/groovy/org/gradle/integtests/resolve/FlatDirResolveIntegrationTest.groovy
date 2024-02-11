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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FlatDirResolveIntegrationTest extends AbstractIntegrationSpec {
    def "can resolve dependencies from a flat dir repository"() {
        given:
        file("repo/a-1.4.jar").createFile()
        file("repo/b.jar").createFile()
        file("repo/c.jar").createFile()

        and:
        buildFile << """
repositories { flatDir { dir 'repo' } }
configurations { compile }
dependencies {
    compile 'group:a:1.4'
    compile 'group:b:2.0'
    compile 'group:c:'
}

task check {
    def files = configurations.compile
    doLast {
        assert files.collect { it.name } == ['a-1.4.jar', 'b.jar', 'c.jar']
    }
}
"""

        expect:
        succeeds "check"
    }

    def "can resolve dynamic versions from a flat dir repository"() {
        given:
        file("repo/a.jar").createFile()
        file("repo/a-1.4.jar").createFile()
        file("repo/a-1.5.jar").createFile()
        file("repo/a-2.0.jar").createFile()
        file("repo/b-1.4-classifier.jar").createFile()
        file("repo/b-1.5-classifier.jar").createFile()
        file("repo/b-1.6.jar").createFile()

        and:
        buildFile << """
repositories { flatDir { dir 'repo' } }
configurations { compile }
dependencies {
    compile 'group:a:1.+'
    compile 'group:b:1.+:classifier'
}

task check {
    def files = configurations.compile
    doLast {
        assert files.collect { it.name } == ['a-1.5.jar', 'b-1.5-classifier.jar']
    }
}
"""

        expect:
        succeeds "check"
    }

    def "can use a classifier to refer to artifacts in flat dir repository"() {
        given:
        file("repo/a-1.4-classifier.jar").createFile()
        file("repo/a-1.4.jar").createFile()

        and:
        buildFile << """
repositories { flatDir { dir 'repo' } }
configurations { compile }
dependencies {
    compile 'group:a:1.4:classifier'
}

task check {
    def files = configurations.compile
    doLast {
        assert files.collect { it.name } == ['a-1.4-classifier.jar']
    }
}
"""

        expect:
        succeeds "check"
    }

    def "can use a type to refer to artifacts in flat dir repository"() {
        given:
        file("repo/a-1.4.zip").createFile()

        and:
        buildFile << """
repositories { flatDir { dir 'repo' } }
configurations { compile }
dependencies {
    compile 'group:a:1.4@zip'
}

task check {
    def files = configurations.compile
    doLast {
        assert files.collect { it.name } == ['a-1.4.zip']
    }
}
"""

        expect:
        succeeds "check"
    }
}
