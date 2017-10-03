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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class MavenDependencyWithGradleMetadataResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile)

    def setup() {
        resolve.prepare()
        server.start()
    }

    def "downloads and caches the module metadata when present"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata().publish()

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGet()
        m.moduleMetadata.expectGet()
        m.artifact.expectGet()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }

        when:
        server.resetExpectations()
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }
    }

    def "skips module metadata when not present and caches result"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").publish()

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGet()
        m.moduleMetadata.expectGetMissing()
        m.artifact.expectGet()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }

        when:
        server.resetExpectations()
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }
    }

    def "downloads and caches the module metadata when present and pom is not present"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withNoPom().withModuleMetadata().publish()

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGetMissing()
        m.moduleMetadata.expectGet()
        m.artifact.expectGet()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }

        when:
        server.resetExpectations()
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }
    }

    def "uses runtime dependencies and files from selected variant"() {
        def b = mavenHttpRepo.module("test", "b", "2.0").publish()
        def m = mavenHttpRepo.module("test", "a", "1.2")
            .dependsOn(b)
            .withModuleMetadata()
        m.artifact(classifier: 'debug')
        m.artifact(classifier: 'release')
        m.publish()
        m.moduleMetadata.file.text = """
{
    "formatVersion": "0.1",
    "variants": [
        {
            "name": "debug",
            "attributes": {
                "buildType": "debug"
            },
            "files": [ { "name": "a-1.2-debug.jar", "url": "a-1.2-debug.jar" } ]
        },
        {
            "name": "release",
            "attributes": {
                "buildType": "release"
            },
            "files": [ { "name": "a-1.2-release.jar", "url": "a-1.2-release.jar" } ]
        }
    ]
}
"""

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
def attr = Attribute.of("buildType", String)
configurations { 
    debug { attributes.attribute(attr, "debug") }
    release { attributes.attribute(attr, "release") }
}
dependencies {
    debug 'test:a:1.2'
    release 'test:a:1.2'
}
task checkDebug {
    doLast { assert configurations.debug.files*.name == ['a-1.2-debug.jar', 'b-2.0.jar'] }
}
task checkRelease {
    doLast { assert configurations.release.files*.name == ['a-1.2-release.jar', 'b-2.0.jar'] }
}
"""

        m.pom.expectGet()
        m.moduleMetadata.expectGet()
        m.artifact(classifier: 'debug').expectGet()
        b.pom.expectGet()
        b.moduleMetadata.expectGetMissing()
        b.artifact.expectGet()

        expect:
        succeeds("checkDebug")

        and:
        server.resetExpectations()
        m.artifact(classifier: 'release').expectGet()

        and:
        succeeds("checkRelease")
    }

    def "variant can define zero files or multiple files"() {
        def b = mavenHttpRepo.module("test", "b", "2.0").publish()
        def m = mavenHttpRepo.module("test", "a", "1.2")
            .dependsOn(b)
            .withModuleMetadata()
        m.artifact(classifier: 'api')
        m.artifact(classifier: 'runtime')
        m.publish()
        m.moduleMetadata.file.text = """
{
    "formatVersion": "0.1",
    "variants": [
        {
            "name": "debug",
            "attributes": {
                "buildType": "debug"
            },
            "files": [ 
                { "name": "a-1.2-api.jar", "url": "a-1.2-api.jar" },
                { "name": "a-1.2-runtime.jar", "url": "a-1.2-runtime.jar" } 
            ]
        },
        {
            "name": "release",
            "attributes": {
                "buildType": "release"
            }
        }
    ]
}
"""

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
def attr = Attribute.of("buildType", String)
configurations { 
    debug { attributes.attribute(attr, "debug") }
    release { attributes.attribute(attr, "release") }
}
dependencies {
    debug 'test:a:1.2'
    release 'test:a:1.2'
}
task checkDebug {
    doLast { assert configurations.debug.files*.name == ['a-1.2-api.jar', 'a-1.2-runtime.jar', 'b-2.0.jar'] }
}
task checkRelease {
    doLast { assert configurations.release.files*.name == ['b-2.0.jar'] }
}
"""

        m.pom.expectGet()
        m.moduleMetadata.expectGet()
        m.artifact(classifier: 'api').expectGet()
        m.artifact(classifier: 'runtime').expectGet()
        b.pom.expectGet()
        b.moduleMetadata.expectGetMissing()
        b.artifact.expectGet()

        expect:
        succeeds("checkDebug")

        and:
        server.resetExpectations()

        and:
        succeeds("checkRelease")
    }

    def "reports failure to locate module"() {
        def m = mavenHttpRepo.module("test", "a", "1.2")

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGetMissing()
        m.moduleMetadata.expectGetMissing()
        m.artifact.expectHeadMissing()

        when:
        fails("checkDeps")

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("""Could not find test:a:1.2.
Searched in the following locations:
    ${m.pom.uri}
    ${m.moduleMetadata.uri}
    ${m.artifact.uri}
Required by:
    project :""")
    }

    def "reports and recovers from failure to download module metadata"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata().publish()

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGet()
        m.moduleMetadata.expectGetBroken()

        when:
        fails("checkDeps")

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not resolve test:a:1.2.")
        failure.assertHasCause("Could not get resource '${m.moduleMetadata.uri}'.")

        when:
        server.resetExpectations()
        // TODO - should not be required
        m.pom.expectHead()
        m.moduleMetadata.expectGet()
        m.artifact.expectGet()
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }
    }

    def "reports failure to parse module metadata"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata().publish()
        m.moduleMetadata.file.text = 'not-really-json'

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGet()
        m.moduleMetadata.expectGet()

        when:
        fails("checkDeps")

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not resolve test:a:1.2.")
        failure.assertHasCause("Could not parse module metadata ${m.moduleMetadata.uri}")
    }

    def "reports failure to accept module metadata with unexpected format version"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata().publish()
        m.moduleMetadata.file.text = m.moduleMetadata.file.text.replace("0.1", "123.67")

        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven { 
        url = '${mavenHttpRepo.uri}' 
        useGradleMetadata() // internal opt-in for now
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGet()
        m.moduleMetadata.expectGet()

        when:
        fails("checkDeps")

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not resolve test:a:1.2.")
        failure.assertHasCause("Could not parse module metadata ${m.moduleMetadata.uri}")
        failure.assertHasCause("Unsupported format version '123.67' specified in module metadata. This version of Gradle supports only format version 0.1")
    }
}
