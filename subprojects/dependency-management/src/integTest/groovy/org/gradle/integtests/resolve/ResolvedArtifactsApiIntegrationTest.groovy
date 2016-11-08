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
class ResolvedArtifactsApiIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
rootProject.name = 'test'
"""
        buildFile << """
allprojects {
    configurations { 
        compile {
            attribute('usage', 'compile')
        }
    }
}
"""
    }

    def "result includes artifacts from local and external components and file dependencies in fixed order"() {
        mavenRepo.module("org", "test", "1.0").publish()
        mavenRepo.module("org", "test2", "1.0").publish()

        settingsFile << """
include 'a', 'b'
"""
        buildFile << """
allprojects {
    repositories { maven { url '$mavenRepo.uri' } }
}
dependencies {
    compile files('test-lib.jar')    
    compile project(':a')
    compile 'org:test:1.0'
    artifacts {
        compile file('test.jar')
    }
}
project(':a') {
    dependencies {
        compile files('a-lib.jar')
        compile project(':b')
        compile 'org:test:1.0'
    }
    artifacts {
        compile file('a.jar')
    }
}
project(':b') {
    dependencies {
        compile files('b-lib.jar')
        compile 'org:test2:1.0'
    }
    artifacts {
        compile file('b.jar')
    }
}

task show {
    doLast {
        println "files: " + configurations.compile.incoming.artifacts.collect { it.file.name }
        println "ids: " + configurations.compile.incoming.artifacts.collect { it.id.displayName }
        println "components: " + configurations.compile.incoming.artifacts.collect { it.id.componentIdentifier.displayName }
    }
}
"""

        when:
        run 'show'

        then:
        outputContains("files: [test-lib.jar, a-lib.jar, b-lib.jar, a.jar, test-1.0.jar, b.jar, test2-1.0.jar")
        outputContains("ids: [test-lib.jar, a-lib.jar, b-lib.jar, a.jar (project :a), test.jar (org:test:1.0), b.jar (project :b), test2.jar (org:test2:1.0)")
        outputContains("components: [test-lib.jar, a-lib.jar, b-lib.jar, project :a, org:test:1.0, project :b, org:test2:1.0")
    }

    def "reports failure to resolve components when artifacts are queried"() {
        buildFile << """
allprojects {
    repositories { maven { url '$mavenHttpRepo.uri' } }
}
dependencies {
    compile 'org:test:1.0+'
    compile 'org:test2:2.0'
}

task show {
    doLast {
        configurations.compile.incoming.artifacts
    }
}
"""

        given:
        mavenHttpRepo.getModuleMetaData('org', 'test').expectGetMissing()
        mavenHttpRepo.directory('org', 'test').expectGetMissing()
        def m = mavenHttpRepo.module('org', 'test2', '2.0').publish()
        m.pom.expectGetBroken()

        when:
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not find any matches for org:test:1.0+ as no versions of org:test are available.")
        failure.assertHasCause("Could not resolve org:test2:2.0.")
    }

    def "reports failure to download artifact when artifacts are queried"() {
        buildFile << """
allprojects {
    repositories { maven { url '$mavenHttpRepo.uri' } }
}
dependencies {
    compile 'org:test:1.0'
    compile 'org:test2:2.0'
}

task show {
    doLast {
        configurations.compile.incoming.artifacts
    }
}
"""

        given:
        def m1 = mavenHttpRepo.module('org', 'test', '1.0').publish()
        m1.pom.expectGet()
        m1.artifact.expectGetMissing()
        def m2 = mavenHttpRepo.module('org', 'test2', '2.0').publish()
        m2.pom.expectGet()
        m2.artifact.expectGet()

        when:
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all artifacts for configuration ':compile'.")
        failure.assertHasCause("Could not find test.jar (org:test:1.0).")
    }

    def "reports failure to query file dependency when artifacts are queried"() {
        buildFile << """
dependencies {
    compile files { throw new RuntimeException('broken') }
    compile files('lib.jar')
}

task show {
    doLast {
        configurations.compile.incoming.artifacts
    }
}
"""
        when:
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all artifacts for configuration ':compile'.")
        failure.assertHasCause("broken")
    }

    def "reports multiple failures to resolve artifacts when artifacts are queried"() {
        buildFile << """
allprojects {
    repositories { maven { url '$mavenHttpRepo.uri' } }
}
dependencies {
    compile 'org:test:1.0'
    compile 'org:test2:2.0'
    compile files { throw new RuntimeException('broken 1') }
    compile files { throw new RuntimeException('broken 2') }
}

task show {
    doLast {
        configurations.compile.incoming.artifacts
    }
}
"""

        given:
        def m1 = mavenHttpRepo.module('org', 'test', '1.0').publish()
        m1.pom.expectGet()
        m1.artifact.expectGetMissing()
        def m2 = mavenHttpRepo.module('org', 'test2', '2.0').publish()
        m2.pom.expectGet()
        m2.artifact.expectGetBroken()

        when:
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all artifacts for configuration ':compile'.")
        failure.assertHasCause("Could not find test.jar (org:test:1.0).")
        failure.assertHasCause("Could not download test2.jar (org:test2:2.0)")
        failure.assertHasCause("broken 1")
        failure.assertHasCause("broken 2")
    }
}
