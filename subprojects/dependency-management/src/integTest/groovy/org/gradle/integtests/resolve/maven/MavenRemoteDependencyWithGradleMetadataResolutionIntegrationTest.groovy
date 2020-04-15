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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Unroll

import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser.FORMAT_VERSION

class MavenRemoteDependencyWithGradleMetadataResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile, "compile").expectDefaultConfiguration("runtime")

    def setup() {
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        server.start()

        settingsFile << "rootProject.name = 'test'"

    }

    @ToBeFixedForInstantExecution
    def "downloads and caches the module metadata when present"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata().publish()

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
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

        when:
        server.resetExpectations()
        m.pom.expectHead()
        m.moduleMetadata.expectHead()
        m.artifact.expectHead()

        executer.withArgument("--refresh-dependencies")
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }
    }

    @ToBeFixedForInstantExecution
    def "skips module metadata when not present and caches result"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").publish()

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGet()
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

        when:
        server.resetExpectations()
        m.pom.expectHead()
        m.artifact.expectHead()

        executer.withArgument("--refresh-dependencies")
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }
    }

    @ToBeFixedForInstantExecution
    def "uses dependencies and files from selected variant"() {
        def c = mavenHttpRepo.module("test", "c", "2.2").publish()
        def b = mavenHttpRepo.module("test", "b", "2.0").publish()
        def a = mavenHttpRepo.module("test", "a", "1.2")
            .dependsOn("test", "ignore-me", "0.1")
            .withModuleMetadata()
        a.artifact(classifier: 'debug')
        a.artifact(classifier: 'release')
        a.publish()
        a.moduleMetadata.file.text = """
{
    "formatVersion": "${FORMAT_VERSION}",
    "variants": [
        {
            "name": "debug",
            "attributes": {
                "buildType": "debug"
            },
            "files": [ { "name": "a-1.2-debug.jar", "url": "a-1.2-debug.jar" } ],
            "dependencies": [ { "group": "test", "module": "b", "version": { "prefers": "2.0", "rejects": [] } } ]
        },
        {
            "name": "release",
            "attributes": {
                "buildType": "release"
            },
            "files": [ { "name": "a-1.2-release.jar", "url": "a-1.2-release.jar" } ],
            "dependencies": [ { "group": "test", "module": "c", "version": { "prefers": "2.2" } } ]
        }
    ]
}
"""

        given:
        "rootProject.name = 'test'"
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
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
    doLast { assert configurations.release.files*.name == ['a-1.2-release.jar', 'c-2.2.jar'] }
}
"""

        a.pom.expectGet()
        a.moduleMetadata.expectGet()
        a.artifact(classifier: 'debug').expectGet()
        b.pom.expectGet()
        b.artifact.expectGet()

        expect:
        succeeds("checkDebug")

        and:
        server.resetExpectations()
        a.artifact(classifier: 'release').expectGet()
        c.pom.expectGet()
        c.artifact.expectGet()

        and:
        succeeds("checkRelease")

        and:
        succeeds("checkDebug")
        succeeds("checkRelease")
    }

    @ToBeFixedForInstantExecution
    def "variant can define zero files or multiple files"() {
        def b = mavenHttpRepo.module("test", "b", "2.0").publish()
        def a = mavenHttpRepo.module("test", "a", "1.2")
            .withModuleMetadata()
        a.artifact(classifier: 'api')
        a.artifact(classifier: 'runtime')
        a.publish()
        a.moduleMetadata.file.text = """
{
    "formatVersion": "${FORMAT_VERSION}",
    "variants": [
        {
            "name": "debug",
            "attributes": {
                "buildType": "debug"
            },
            "files": [
                { "name": "a-1.2-api.jar", "url": "a-1.2-api.jar" },
                { "name": "a-1.2-runtime.jar", "url": "a-1.2-runtime.jar" }
            ],
            "dependencies": [ { "group": "test", "module": "b", "version": { "prefers": "2.0" } } ]
        },
        {
            "name": "release",
            "attributes": {
                "buildType": "release"
            },
            "dependencies": [ { "group": "test", "module": "b", "version": { "prefers": "2.0" } } ]
        }
    ]
}
"""

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
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

        a.pom.expectGet()
        a.moduleMetadata.expectGet()
        a.artifact(classifier: 'api').expectGet()
        a.artifact(classifier: 'runtime').expectGet()
        b.pom.expectGet()
        b.artifact.expectGet()

        expect:
        succeeds("checkDebug")

        and:
        server.resetExpectations()

        and:
        succeeds("checkRelease")

        and:
        // Cached
        succeeds("checkDebug")
        succeeds("checkRelease")

        and:
        server.resetExpectations()
        a.pom.expectHead()
        a.moduleMetadata.expectHead()
        a.artifact(classifier: 'api').expectHead()
        a.artifact(classifier: 'runtime').expectHead()
        b.pom.expectHead()
        b.artifact.expectHead()

        executer.withArgument("--refresh-dependencies")
        succeeds("checkDebug")
    }

    @ToBeFixedForInstantExecution
    def "variant can define files whose names are different to their maven contention location"() {
        def a = mavenHttpRepo.module("test", "a", "1.2")
            .withModuleMetadata()
        a.artifact(type: 'zip')
        a.artifact(classifier: 'extra')
        a.publish()
        a.moduleMetadata.file.text = """
{
    "formatVersion": "${FORMAT_VERSION}",
    "variants": [
        {
            "name": "lot-o-files",
            "files": [
                { "name": "a_main.jar", "url": "a-1.2.jar" },
                { "name": "a_extra.jar", "url": "a-1.2-extra.jar" },
                { "name": "a.zip", "url": "a-1.2.zip" }
            ]
        }
    ]
}
"""

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
    }
}
configurations {
    debug
}
dependencies {
    debug 'test:a:1.2'
}
task checkDebug {
    doLast { assert configurations.debug.files*.name == ['a_main.jar', 'a_extra.jar', 'a.zip'] }
}
"""

        a.pom.expectGet()
        a.moduleMetadata.expectGet()
        a.getArtifact().expectGet()
        a.getArtifact(type: 'zip').expectGet()
        a.getArtifact(classifier: 'extra').expectGet()

        expect:
        succeeds("checkDebug")

        and:
        server.resetExpectations()

        and:
        // cached
        succeeds("checkDebug")

        and:
        server.resetExpectations()
        a.pom.expectHead()
        a.moduleMetadata.expectHead()
        a.getArtifact().expectHead()
        a.getArtifact(type: 'zip').expectHead()
        a.getArtifact(classifier: 'extra').expectHead()

        executer.withArgument("--refresh-dependencies")
        succeeds("checkDebug")
    }

    @ToBeFixedForInstantExecution
    def "variant can define files whose names and locations do not match maven convention"() {
        def a = mavenHttpRepo.module("test", "a", "1.2")
            .withModuleMetadata()
        a.getArtifact("file1.jar").file << "file 1"
        a.getArtifact("file2.jar").file << "file 2"
        a.getArtifact("../sibling/file3.jar").file << "file 3"
        a.getArtifact("child/file4.jar").file << "file 4"
        a.getArtifact("../../../a-1.2-5.jar").file << "file 5"
        a.publish()
        a.moduleMetadata.file.text = """
{
    "formatVersion": "${FORMAT_VERSION}",
    "variants": [
        {
            "name": "lot-o-files",
            "files": [
                { "name": "file1.jar", "url": "file1.jar" },
                { "name": "a-1.2.jar", "url": "file2.jar" },
                { "name": "a-3.jar", "url": "../sibling/file3.jar" },
                { "name": "file4.jar", "url": "child/file4.jar" },
                { "name": "a_5.jar", "url": "/repo/a-1.2-5.jar" }
            ]
        }
    ]
}
"""

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
    }
}
configurations {
    debug
}
dependencies {
    debug 'test:a:1.2'
}
task checkDebug {
    doLast { assert configurations.debug.files*.name == ['file1.jar', 'a-1.2.jar', 'a-3.jar', 'file4.jar', 'a_5.jar'] }
}
"""

        a.pom.expectGet()
        a.moduleMetadata.expectGet()
        a.getArtifact("file1.jar").expectGet()
        a.getArtifact("file2.jar").expectGet()
        a.getArtifact("../sibling/file3.jar").expectGet()
        a.getArtifact("child/file4.jar").expectGet()
        a.getArtifact("../../../a-1.2-5.jar").expectGet()

        expect:
        succeeds("checkDebug")

        and:
        server.resetExpectations()

        and:
        // Cached result
        succeeds("checkDebug")

        and:
        server.resetExpectations()
        a.pom.expectHead()
        a.moduleMetadata.expectHead()
        a.getArtifact("file1.jar").expectHead()
        a.getArtifact("file2.jar").expectHead()
        a.getArtifact("../sibling/file3.jar").expectHead()
        a.getArtifact("child/file4.jar").expectHead()
        a.getArtifact("../../../a-1.2-5.jar").expectHead()

        executer.withArgument("--refresh-dependencies")
        succeeds("checkDebug")
    }

    def "module with module metadata can depend on another module with module metadata"() {
        def c = mavenHttpRepo.module("test", "c", "preview")
            .withModuleMetadata()
        c.artifact(classifier: 'debug')
        c.publish()
        c.moduleMetadata.file.text = """
{
    "formatVersion": "${FORMAT_VERSION}",
    "variants": [
        {
            "name": "debug",
            "attributes": {
                "buildType": "debug"
            },
            "files": [ { "name": "c-preview-debug.jar", "url": "c-preview-debug.jar" } ]
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

        def b = mavenHttpRepo.module("test", "b", "2.0")
            .dependsOn(c)
            .withModuleMetadata()
        b.publish()

        def a = mavenHttpRepo.module("test", "a", "1.2")
            .withModuleMetadata()
        a.artifact(classifier: 'debug')
        a.publish()
        a.moduleMetadata.file.text = """
{
    "formatVersion": "${FORMAT_VERSION}",
    "variants": [
        {
            "name": "debug",
            "attributes": {
                "buildType": "debug"
            },
            "files": [ { "name": "a-1.2-debug.jar", "url": "a-1.2-debug.jar" } ],
            "dependencies": [ { "group": "test", "module": "b", "version": { "prefers": "2.0" } } ]
        },
        {
            "name": "release",
            "attributes": {
                "buildType": "release"
            },
            "dependencies": [ { "group": "test", "module": "c", "version": { "prefers": "preview" }} ]
        }
    ]
}
"""

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
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
    doLast { assert configurations.debug.files*.name == ['a-1.2-debug.jar', 'b-2.0.jar', 'c-preview-debug.jar'] }
}
task checkRelease {
    doLast { assert configurations.release.files*.name == [] }
}
"""

        a.pom.expectGet()
        a.moduleMetadata.expectGet()
        a.artifact(classifier: 'debug').expectGet()
        b.pom.expectGet()
        b.moduleMetadata.expectGet()
        b.artifact.expectGet()
        c.pom.expectGet()
        c.moduleMetadata.expectGet()
        c.artifact(classifier: 'debug').expectGet()

        expect:
        succeeds("checkDebug")

        and:
        server.resetExpectations()

        and:
        succeeds("checkRelease")
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "consumer can use attribute of type #type"() {
        def a = mavenHttpRepo.module("test", "a", "1.2")
            .withModuleMetadata()
        a.artifact(classifier: 'debug')
        a.publish()
        a.moduleMetadata.file.text = """
{
    "formatVersion": "${FORMAT_VERSION}",
    "variants": [
        {
            "name": "debug",
            "attributes": {
                "buildType": ${encodedDebugValue}
            },
            "files": [
                { "name": "a-1.2-debug.jar", "url": "a-1.2-debug.jar" }
            ]
        },
        {
            "name": "release",
            "attributes": {
                "buildType": ${encodedReleaseValue}
            }
        }
    ]
}
"""

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
    }
}

enum BuildTypeEnum {
    debug, release
}
interface BuildType extends Named {
}

def attr = Attribute.of("buildType", ${type})
configurations {
    debug { attributes.attribute(attr, ${debugValue}) }
    release { attributes.attribute(attr, ${releaseValue}) }
}
dependencies {
    debug 'test:a:1.2'
    release 'test:a:1.2'
}
task checkDebug {
    doLast { assert configurations.debug.files*.name == ['a-1.2-debug.jar'] }
}
task checkRelease {
    doLast { assert configurations.release.files*.name == [] }
}
"""

        a.pom.expectGet()
        a.moduleMetadata.expectGet()
        a.artifact(classifier: 'debug').expectGet()

        expect:
        succeeds("checkDebug")

        and:
        server.resetExpectations()

        and:
        succeeds("checkRelease")

        // Cached
        succeeds("checkDebug")
        succeeds("checkRelease")

        where:
        encodedDebugValue | encodedReleaseValue | type            | debugValue                          | releaseValue
        '"debug"'         | '"release"'         | "BuildTypeEnum" | "BuildTypeEnum.debug"               | "BuildTypeEnum.release"
        '"debug"'         | '"release"'         | "BuildType"     | "objects.named(BuildType, 'debug')" | "objects.named(BuildType, 'release')"
        '"debug"'         | '"release"'         | "String"        | "'debug'"                           | "'release'"
        "true"            | "false"             | "Boolean"       | "true"                              | "false"
    }

    @ToBeFixedForInstantExecution
    def "reports and recovers from failure to locate module"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata()

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGetMissing()

        when:
        fails("checkDeps")

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("""Could not find test:a:1.2.
Searched in the following locations:
  - ${m.pom.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
    project :""")

        when:
        server.resetExpectations()
        m.publish()
        m.pom.expectGet()
        m.moduleMetadata.expectGet()
        m.artifact.expectGet()

        succeeds("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("test:a:1.2")
            }
        }
    }

    @ToBeFixedForInstantExecution
    def "reports and recovers from failure to download module metadata"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata().publish()

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
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

    @ToBeFixedForInstantExecution
    def "reports failure to parse module metadata"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata().publish()
        m.moduleMetadata.file.text = 'not-really-json'

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
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

        when:
        server.resetExpectations()
        m.pom.expectHead()
        m.moduleMetadata.expectHead()

        fails("checkDeps")

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not resolve test:a:1.2.")
        failure.assertHasCause("Could not parse module metadata ${m.moduleMetadata.uri}")
    }

    def "reports failure to locate files"() {
        def m = mavenHttpRepo.module("test", "a", "1.2").withModuleMetadata()
        m.artifact(classifier: 'extra')
        m.getArtifact("file1.jar").file << "file 1"
        m.getArtifact("../file2.jar").file << "file 2"
        m.publish()
        m.moduleMetadata.file.text = """
{
    "formatVersion": "${FORMAT_VERSION}",
    "variants": [
        {
            "name": "lot-o-files",
            "files": [
                { "name": "a1.jar", "url": "file1.jar" },
                { "name": "a2.jar", "url": "../file2.jar" },
                { "name": "a3.jar", "url": "a-1.2-extra.jar" }
            ]
        }
    ]
}
"""

        given:
        buildFile << """
repositories {
    maven {
        url = '${mavenHttpRepo.uri}'
    }
}
configurations { compile }
dependencies {
    compile 'test:a:1.2'
}
"""

        m.pom.expectGet()
        m.moduleMetadata.expectGet()
        m.artifact(classifier: 'extra').expectGetMissing()
        m.getArtifact("file1.jar").expectGetMissing()
        m.getArtifact("../file2.jar").expectGetMissing()

        when:
        fails("checkDeps")

        then:
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("""Could not find a1.jar (test:a:1.2).
Searched in the following locations:
    ${m.getArtifact("file1.jar").uri}""")
        failure.assertHasCause("""Could not find a2.jar (test:a:1.2).
Searched in the following locations:
    ${m.getArtifact("../file2.jar").uri}""")
        failure.assertHasCause("""Could not find a3.jar (test:a:1.2).
Searched in the following locations:
    ${m.artifact(classifier: 'extra').uri}""")

        when:
        server.resetExpectations()

        then:
        // cached
        fails("checkDeps")

        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("""Could not find a1.jar (test:a:1.2).
Searched in the following locations:
    ${m.getArtifact("file1.jar").uri}""")
        failure.assertHasCause("""Could not find a2.jar (test:a:1.2).
Searched in the following locations:
    ${m.getArtifact("../file2.jar").uri}""")
        failure.assertHasCause("""Could not find a3.jar (test:a:1.2).
Searched in the following locations:
    ${m.artifact(classifier: 'extra').uri}""")
    }
}
