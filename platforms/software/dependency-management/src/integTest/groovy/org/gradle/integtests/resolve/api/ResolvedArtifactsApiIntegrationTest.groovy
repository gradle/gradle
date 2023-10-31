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
import org.gradle.internal.component.ResolutionFailureHandler

@FluidDependenciesResolveTest
class ResolvedArtifactsApiIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        createDirs("a", "b")
        settingsFile << """
rootProject.name = 'test'
include 'a', 'b'
"""
        buildFile << """
def usage = Attribute.of('usage', String)
def flavor = Attribute.of('flavor', String)
def buildType = Attribute.of('buildType', String)

allprojects {
    dependencies {
       attributesSchema {
          attribute(usage)
          attribute(flavor)
          attribute(buildType)
       }
    }
    configurations {
        compile
        create("default") {
            extendsFrom compile
        }
    }
}
"""

        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"
    }

    def "result includes artifacts from local and external components and file dependencies in fixed order"() {
        mavenRepo.module("org", "test", "1.0").publish()
        mavenRepo.module("org", "test2", "1.0").publish()

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
    inputs.files configurations.compile
    def artifacts = configurations.compile.${expression}
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "ids: " + artifacts.collect { it.id.displayName }
        println "unique ids: " + artifacts.collect { it.id }.unique()
        println "display-names: " + artifacts.collect { it.toString() }
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "unique components: " + artifacts.collect { it.id.componentIdentifier }.unique()
        println "variants: " + artifacts.collect { it.variant.attributes }
        assert artifacts.failures.empty
    }
}
"""

        when:
        run 'show'

        then:
        outputContains("files: [test-lib.jar, a.jar, a-lib.jar, test-1.0.jar, b.jar, b-lib.jar, test2-1.0.jar]")
        outputContains("ids: [test-lib.jar, a.jar (project :a), a-lib.jar, test-1.0.jar (org:test:1.0), b.jar (project :b), b-lib.jar, test2-1.0.jar (org:test2:1.0)]")
        outputContains("unique ids: [test-lib.jar, a.jar (project :a), a-lib.jar, test-1.0.jar (org:test:1.0), b.jar (project :b), b-lib.jar, test2-1.0.jar (org:test2:1.0)]")
        outputContains("display-names: [test-lib.jar, a.jar (project :a), a-lib.jar, test-1.0.jar (org:test:1.0), b.jar (project :b), b-lib.jar, test2-1.0.jar (org:test2:1.0)]")
        outputContains("components: [test-lib.jar, project :a, a-lib.jar, org:test:1.0, project :b, b-lib.jar, org:test2:1.0]")
        outputContains("unique components: [test-lib.jar, project :a, a-lib.jar, org:test:1.0, project :b, b-lib.jar, org:test2:1.0]")
        outputContains("variants: [{artifactType=jar}, {artifactType=jar}, {artifactType=jar}, {artifactType=jar, org.gradle.status=release}, {artifactType=jar}, {artifactType=jar}, {artifactType=jar, org.gradle.status=release}]")

        where:
        expression                                                     | _
        "incoming.artifacts"                                           | _
        "incoming.artifactView({}).artifacts"                          | _
        "incoming.artifactView({ componentFilter { true }}).artifacts" | _
    }

    def "result includes declared variant for local dependencies"() {
        buildFile << """
allprojects {
    configurations.compile.attributes.attribute(usage, 'compile')
}
dependencies {
    compile project(':a')
}
project(':a') {
    configurations {
        compile {
            attributes.attribute(buildType, 'debug')
            outgoing {
                variants {
                    var1 {
                        artifact file('a1.jar')
                        attributes.attribute(flavor, 'one')
                    }
                }
            }
        }
    }
    dependencies {
        compile project(':b')
    }
}
project(':b') {
    configurations {
        compile {
            outgoing {
                variants {
                    var1 {
                        artifact file('b2.jar')
                        attributes.attribute(flavor, 'two')
                    }
                }
            }
        }
    }
}

task show {
    inputs.files configurations.compile
    def artifacts = configurations.compile.${expression}
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "ids: " + artifacts.collect { it.id.displayName }
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "variants: " + artifacts.collect { it.variant.attributes }
        assert artifacts.failures.empty
    }
}
"""

        when:
        run 'show'

        then:
        outputContains("files: [a1.jar, b2.jar]")
        outputContains("ids: [a1.jar (project :a), b2.jar (project :b)]")
        outputContains("components: [project :a, project :b]")
        outputContains("variants: [{artifactType=jar, buildType=debug, flavor=one, usage=compile}, {artifactType=jar, flavor=two, usage=compile}]")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
    }

    def "applies compatibility rules to select variants"() {
        buildFile << """
class OneRule implements AttributeCompatibilityRule<String> {
    void execute(CompatibilityCheckDetails<String> details) {
        if (details.consumerValue == 'preview' && details.producerValue == 'one') {
            details.compatible()
        }
    }
}
class TwoRule implements AttributeCompatibilityRule<String> {
    void execute(CompatibilityCheckDetails<String> details) {
        if (details.consumerValue == 'preview' && details.producerValue == 'two') {
            details.compatible()
        }
    }
}

allprojects {
    configurations.compile.attributes.attribute(usage, 'compile')
}

dependencies {
    compile project(':a')
}

configurations.compile.attributes.attribute(flavor, 'preview')

project(':a') {
    dependencies.attributesSchema.attribute(flavor).compatibilityRules.add(OneRule)
    task oneJar(type: Jar) { archiveBaseName = 'a1' }
    task twoJar(type: Jar) { archiveBaseName = 'a2' }
    tasks.withType(Jar) { destinationDirectory = buildDir }
    configurations {
        compile {
            attributes.attribute(buildType, 'debug')
            outgoing {
                variants {
                    var1 {
                        artifact oneJar
                        attributes.attribute(flavor, 'one')
                    }
                    var2 {
                        artifact twoJar
                        attributes.attribute(flavor, 'two')
                    }
                }
            }
        }
    }
    dependencies {
        compile project(':b')
    }
}
project(':b') {
    dependencies.attributesSchema.attribute(flavor).compatibilityRules.add(TwoRule)
    task oneJar(type: Jar) { archiveBaseName = 'b1' }
    task twoJar(type: Jar) { archiveBaseName = 'b2' }
    tasks.withType(Jar) { destinationDirectory = buildDir }
    configurations {
        compile {
            outgoing {
                variants {
                    var1 {
                        artifact oneJar
                        attributes.attribute(flavor, 'one')
                    }
                    var2 {
                        artifact twoJar
                        attributes.attribute(flavor, 'two')
                    }
                }
            }
        }
    }
}

task show {
    inputs.files configurations.compile.${expression}.artifactFiles
    def artifacts = configurations.compile.${expression}
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "ids: " + artifacts.collect { it.id.displayName }
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "variants: " + artifacts.collect { it.variant.attributes }
        assert artifacts.failures.empty
    }
}
"""

        when:
        run 'show'

        then:
        outputContains("files: [a1.jar, b2.jar]")
        outputContains("ids: [a1.jar (project :a), b2.jar (project :b)]")
        outputContains("components: [project :a, project :b]")
        outputContains("variants: [{artifactType=jar, buildType=debug, flavor=one, usage=compile}, {artifactType=jar, flavor=two, usage=compile}]")

        and:
        result.assertTasksExecuted(':a:oneJar', ':b:twoJar', ':show')

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
    }

    def "applies disambiguation rules to select variants"() {
        buildFile << """
class OneRule implements AttributeDisambiguationRule<String> {
    void execute(MultipleCandidatesDetails<String> details) {
        details.closestMatch('one')
    }
}
class TwoRule implements AttributeDisambiguationRule<String> {
    void execute(MultipleCandidatesDetails<String> details) {
        details.closestMatch('two')
    }
}

allprojects {
    configurations.compile.attributes.attribute(usage, 'compile')
}

dependencies {
    compile project(':a')
}

project(':a') {
    dependencies.attributesSchema.attribute(flavor).disambiguationRules.add(OneRule)
    task oneJar(type: Jar) { archiveBaseName = 'a1' }
    task twoJar(type: Jar) { archiveBaseName = 'a2' }
    tasks.withType(Jar) { destinationDirectory = buildDir }
    configurations {
        compile {
            attributes.attribute(buildType, 'debug')
            outgoing {
                variants {
                    var1 {
                        artifact oneJar
                        attributes.attribute(flavor, 'one')
                    }
                    var2 {
                        artifact twoJar
                        attributes.attribute(flavor, 'two')
                    }
                }
            }
        }
    }
    dependencies {
        compile project(':b')
    }
}
project(':b') {
    dependencies.attributesSchema.attribute(flavor).disambiguationRules.add(TwoRule)
    task oneJar(type: Jar) { archiveBaseName = 'b1' }
    task twoJar(type: Jar) { archiveBaseName = 'b2' }
    tasks.withType(Jar) { destinationDirectory = buildDir }
    configurations {
        compile {
            outgoing {
                variants {
                    var1 {
                        artifact oneJar
                        attributes.attribute(flavor, 'one')
                    }
                    var2 {
                        artifact twoJar
                        attributes.attribute(flavor, 'two')
                    }
                }
            }
        }
    }
}

task show {
    inputs.files configurations.compile.${expression}.artifactFiles
    def artifacts = configurations.compile.${expression}
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "ids: " + artifacts.collect { it.id.displayName }
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "variants: " + artifacts.collect { it.variant.attributes }
        assert artifacts.failures.empty
    }
}
"""

        when:
        run 'show'

        then:
        outputContains("files: [a1.jar, b2.jar]")
        outputContains("ids: [a1.jar (project :a), b2.jar (project :b)]")
        outputContains("components: [project :a, project :b]")
        outputContains("variants: [{artifactType=jar, buildType=debug, flavor=one, usage=compile}, {artifactType=jar, flavor=two, usage=compile}]")

        and:
        result.assertTasksExecuted(':a:oneJar', ':b:twoJar', ':show')

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
    }

    def "reports failure when multiple compatible variants available"() {
        buildFile << """
allprojects {
    configurations.compile.attributes.attribute(usage, 'compile')
}

dependencies {
    compile project(':a')
}

project(':a') {
    task oneJar(type: Jar) { archiveBaseName = 'a1' }
    task twoJar(type: Jar) { archiveBaseName = 'a2' }
    configurations {
        compile {
            attributes.attribute(buildType, 'debug')
            outgoing {
                variants {
                    var1 {
                        artifact oneJar
                        attributes.attribute(flavor, 'one')
                    }
                    var2 {
                        artifact twoJar
                        attributes.attribute(flavor, 'two')
                    }
                }
            }
        }
    }
    dependencies {
        compile project(':b')
    }
}
project(':b') {
    task oneJar(type: Jar) { archiveBaseName = 'b1' }
    task twoJar(type: Jar) { archiveBaseName = 'b2' }
    configurations {
        compile {
            outgoing {
                variants {
                    var1 {
                        artifact oneJar
                        attributes.attribute(flavor, 'one')
                    }
                    var2 {
                        artifact twoJar
                        attributes.attribute(flavor, 'two')
                    }
                }
            }
        }
    }
}

task show {
    def artifacts = configurations.compile.${expression}
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        throw new RuntimeException()
    }
}
"""

        when:
        fails 'show'

        then:
        failure.assertHasCause("""The consumer was configured to find attribute 'usage' with value 'compile'. However we cannot choose between the following variants of project :a:
  - Configuration ':a:compile' variant var1 declares attribute 'usage' with value 'compile':
      - Unmatched attributes:
          - Provides artifactType 'jar' but the consumer didn't ask for it
          - Provides buildType 'debug' but the consumer didn't ask for it
          - Provides flavor 'one' but the consumer didn't ask for it
  - Configuration ':a:compile' variant var2 declares attribute 'usage' with value 'compile':
      - Unmatched attributes:
          - Provides artifactType 'jar' but the consumer didn't ask for it
          - Provides buildType 'debug' but the consumer didn't ask for it
          - Provides flavor 'two' but the consumer didn't ask for it""")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
    }

    def "result includes consumer-provided variants"() {
        mavenRepo.module("org", "test", "1.0").publish()

        buildFile << """

import org.gradle.api.artifacts.transform.TransformParameters

abstract class VariantArtifactTransform implements TransformAction<TransformParameters.None> {
    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def output = outputs.file("transformed-" + inputArtifact.get().asFile.name)
        output << "transformed"
    }
}

allprojects {
    repositories { maven { url '$mavenRepo.uri' } }
    configurations.compile.attributes.attribute(usage, 'compile')
}

dependencies {
    compile files('test-lib.jar')
    compile project(':a')
    compile project(':b')
    compile 'org:test:1.0'
    registerTransform(VariantArtifactTransform) {
        from.attribute(usage, "compile")
        to.attribute(usage, "transformed")
    }
}

project(':a') {
    configurations {
        compile {
            attributes.attribute(buildType, 'debug')
            outgoing {
                variants {
                    var1 {
                        artifact file('a1.jar')
                        attributes.attribute(flavor, 'one')
                    }
                }
            }
        }
    }
}

project(':b') {
    artifacts {
        compile file('b2.jar')
    }
}

task show {
    inputs.files configurations.compile
    def artifacts = configurations.compile.incoming.artifactView {
        attributes({it.attribute(usage, 'transformed')})
    }.artifacts
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "variants: " + artifacts.collect { it.variant.attributes }
        assert artifacts.failures.empty
    }
}
"""

        when:
        run 'show'

        then:
        outputContains("files: [test-lib.jar, transformed-a1.jar, transformed-b2.jar, test-1.0.jar]")
        outputContains("components: [test-lib.jar, project :a, project :b, org:test:1.0]")
        outputContains("variants: [{artifactType=jar}, {artifactType=jar, buildType=debug, flavor=one, usage=transformed}, {artifactType=jar, usage=transformed}, {artifactType=jar, org.gradle.status=release}]")
    }

    def "more than one local file can have a given base name"() {
        settingsFile << """
include 'a', 'b'
"""
        buildFile << """
dependencies {
    compile project(':a')
    compile files('lib.jar')
}
project(':a') {
    dependencies {
        compile project(':b')
        compile rootProject.files('lib.jar')
        compile files('lib.jar')
    }
    artifacts {
        compile file('one/lib.jar')
        compile file('two/lib.jar')
        compile rootProject.file('lib.jar')
    }
}
project(':b') {
    dependencies {
        compile rootProject.files('lib.jar')
        compile files('lib.jar')
    }
    artifacts {
        compile rootProject.file('lib.jar')
    }
}

task show {
    inputs.files configurations.compile
    def artifacts = configurations.compile.${expression}
    def rootDir = rootProject.projectDir.toPath()
    doLast {
        println "files: " + artifacts.collect { rootDir.relativize(it.file.toPath()).toString().replace(File.separator, '/') }
        println "ids: " + artifacts.collect { it.id.displayName }
        println "unique ids: " + artifacts.collect { it.id }.unique()
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "unique components: " + artifacts.collect { it.id.componentIdentifier }.unique()
        assert artifacts.failures.empty
    }
}
"""

        when:
        run 'show'

        then:
        outputContains("files: [lib.jar, a/one/lib.jar, a/two/lib.jar, lib.jar, a/lib.jar, lib.jar, b/lib.jar]")
        outputContains("ids: [lib.jar, lib.jar (project :a), lib.jar (project :a), lib.jar (project :a), lib.jar, lib.jar (project :b), lib.jar]")
        outputContains("unique ids: [lib.jar, lib.jar (project :a), lib.jar (project :a), lib.jar (project :a), lib.jar, lib.jar (project :b), lib.jar]")
        outputContains("components: [lib.jar, project :a, project :a, project :a, lib.jar, project :b, lib.jar]")
        outputContains("unique components: [lib.jar, project :a, lib.jar, project :b, lib.jar]")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
        "incoming.artifactView({lenient(true)}).artifacts"            | _
    }

    def "reports failure to resolve components when artifacts are queried"() {
        buildFile << """
allprojects {
    repositories {
        maven {
            url '$mavenHttpRepo.uri'
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }
}
dependencies {
    compile 'org:test:1.0+'
    compile 'org:test2:2.0'
}

${showFailuresTask(expression)}
"""

        given:
        mavenHttpRepo.getModuleMetaData('org', 'test').expectGetMissing()
        mavenHttpRepo.directory('org', 'test').expectGetMissing()
        def m = mavenHttpRepo.module('org', 'test2', '2.0').publish()
        m.pom.expectGetBroken()

        when:
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all artifacts for configuration ':compile'.")
        failure.assertHasCause("Could not find any matches for org:test:1.0+ as no versions of org:test are available.")
        failure.assertHasCause("Could not resolve org:test2:2.0.")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
        "incoming.artifactView({lenient(false)}).artifacts"           | _
    }

    def "reports failure to select configurations when artifacts are queried"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
def volume = Attribute.of('volume', Integer)
allprojects {
    dependencies.attributesSchema.attribute(volume)
}
configurations.compile.attributes.attribute(volume, 11)

dependencies {
    compile project(':a')
    compile project(':b')
}

project(':a') {
    configurations.compile.attributes.attribute(volume, 8)
}
project(':b') {
    configurations.compile.attributes.attribute(volume, 9)
}

${showFailuresTask(expression)}
"""

        when:
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all artifacts for configuration ':compile'.")
        failure.assertHasCause("""No matching variant of project :a was found. The consumer was configured to find attribute 'volume' with value '11' but:
  - Variant 'compile' capability test:a:unspecified:
      - Incompatible because this component declares attribute 'volume' with value '8' and the consumer needed attribute 'volume' with value '11'""")
        failure.assertHasCause("""No matching variant of project :b was found. The consumer was configured to find attribute 'volume' with value '11' but:
  - Variant 'compile' capability test:b:unspecified:
      - Incompatible because this component declares attribute 'volume' with value '9' and the consumer needed attribute 'volume' with value '11'""")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
        "incoming.artifactView({lenient(false)}).artifacts"           | _
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

${showFailuresTask(expression)}
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
        failure.assertHasCause("Could not find test-1.0.jar (org:test:1.0).")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
        "incoming.artifactView({lenient(false)}).artifacts"           | _
    }

    @ToBeFixedForConfigurationCache(because = "error reporting is different when CC is enabled")
    def "reports failure to query file dependency when artifacts are queried"() {
        buildFile << """
dependencies {
    compile files { throw new RuntimeException('broken') }
    compile files('lib.jar')
}

${showFailuresTask(expression)}
"""
        when:
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all artifacts for configuration ':compile'.")
        failure.assertHasCause("broken")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
        "incoming.artifactView({lenient(false)}).artifacts"           | _
    }

    @ToBeFixedForConfigurationCache(because = "error reporting is different when CC is enabled")
    def "reports multiple failures to resolve artifacts when artifacts are queried"() {
        settingsFile << "include 'a'"
        buildFile << """
allprojects {
    repositories { maven { url '$mavenHttpRepo.uri' } }
}
dependencies {
    compile 'org:test:1.0'
    compile 'org:test2:2.0'
    compile files { throw new RuntimeException('broken 1') }
    compile files { throw new RuntimeException('broken 2') }
    compile project(':a')
}

project(':a') {
    configurations.default.outgoing.variants {
        v1 { }
        v2 { }
    }
}

${showFailuresTask(expression)}
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
        failure.assertHasCause("Could not find test-1.0.jar (org:test:1.0).")
        failure.assertHasCause("Could not download test2-2.0.jar (org:test2:2.0)")
        failure.assertHasCause("broken 1")
        failure.assertHasCause("broken 2")
        failure.assertHasCause("More than one variant of project :a matches the consumer attributes")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
        "incoming.artifactView({lenient(false)}).artifacts"           | _
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "lenient artifact view reports failure to resolve graph and artifacts"() {
        settingsFile << "include 'a', 'b'"

        buildFile << """
allprojects {
    repositories { maven { url '$mavenHttpRepo.uri' } }
}
dependencies {
    compile 'org:missing-module:1.0'
    compile 'org:missing-artifact:1.0'
    compile 'org:broken-artifact:1.0'
    compile 'org:found:2.0'
    compile files('lib.jar')
    compile files { throw new RuntimeException('broken') }
    compile project(':a')
    compile project(':b')
}

configurations.compile.attributes.attribute(usage, "compile")

project(':a') {
    configurations.default.outgoing.variants {
        v1 { }
        v2 { }
    }
}

project(':b') {
    configurations.compile.attributes.attribute(usage, "broken")
}

task resolveLenient {
    def lenientView = configurations.compile.incoming.artifactView({lenient(true)})
    doLast {
        def resolvedFiles = ['lib.jar', 'found-2.0.jar']
        assert lenientView.files.collect { it.name } == resolvedFiles
        assert lenientView.artifacts.collect { it.file.name } == resolvedFiles
        assert lenientView.artifacts.artifactFiles.collect { it.name } == resolvedFiles
        lenientView.artifacts.failures.eachWithIndex { f, i -> println "failure \${i+1}: \$f.message" }
    }
}
"""

        given:
        def m0 = mavenHttpRepo.module('org', 'missing-module', '1.0')
        m0.pom.expectGetMissing()

        def m1 = mavenHttpRepo.module('org', 'missing-artifact', '1.0').publish()
        m1.pom.expectGet()
        m1.artifact.expectGetMissing()

        def m2 = mavenHttpRepo.module('org', 'broken-artifact', '1.0').publish()
        m2.pom.expectGet()
        m2.artifact.expectGetBroken()

        def m3 = mavenHttpRepo.module('org', 'found', '2.0').publish()
        m3.pom.expectGet()
        m3.artifact.expectGet()

        expect:
        succeeds 'resolveLenient'

        outputContains("failure 1: Could not find org:missing-module:1.0.")
        outputContains("failure 2: Could not resolve project :b.")
        outputContains("failure 3: broken")
        outputContains("""failure 4: Could not find missing-artifact-1.0.jar (org:missing-artifact:1.0).
Searched in the following locations:
    ${m1.artifact.uri}""")
        outputContains("failure 5: Could not download broken-artifact-1.0.jar (org:broken-artifact:1.0)")
        outputContains("""failure 6: The consumer was configured to find attribute 'usage' with value 'compile'. However we cannot choose between the following variants of project :a:
  - Configuration ':a:default' variant v1:
      - Unmatched attribute:
          - Doesn't say anything about usage (required 'compile')
  - Configuration ':a:default' variant v2:
      - Unmatched attribute:
          - Doesn't say anything about usage (required 'compile')""")
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "successfully resolved local artifacts are built when lenient file view used as task input"() {
        createDirs("a", "b", "c")
        settingsFile << "include 'a', 'b', 'c'"

        buildFile << """
allprojects {
    repositories { maven { url '$mavenHttpRepo.uri' } }
    tasks.withType(Jar) {
        archiveFileName = project.name + '-' + name + ".jar"
        destinationDirectory = buildDir
    }
}
dependencies {
    compile 'org:missing-module:1.0'
    compile project(':a')
    compile project(':b')
}

configurations.compile.attributes.attribute(usage, "compile")

project(':a') {
    task jar1(type: Jar)
    task jar2(type: Jar)
    dependencies {
        compile project(':c')
    }
    configurations.default.outgoing.variants {
        v1 { artifact jar1 }
        v2 { artifact jar2 }
    }
}

project(':b') {
    configurations.compile.attributes.attribute(usage, "broken")
    task jar1(type: Jar)
    artifacts { compile jar1 }
}

project(':c') {
    task jar1(type: Jar)
    artifacts { compile jar1 }
}

task resolveLenient {
    def lenientView = configurations.compile.incoming.artifactView({lenient(true)})
    inputs.files lenientView.files
    doLast {
        def resolvedFiles = ['c-jar1.jar']
        assert lenientView.files.collect { it.name } == resolvedFiles
        assert lenientView.artifacts.collect { it.file.name } == resolvedFiles
        assert lenientView.artifacts.artifactFiles.collect { it.name } == resolvedFiles
        assert lenientView.artifacts.failures.size() == 3
    }
}
"""

        given:
        def m0 = mavenHttpRepo.module('org', 'missing-module', '1.0')
        m0.pom.expectGetMissing()

        expect:
        succeeds 'resolveLenient'
        result.assertTasksExecuted(":c:jar1", ":resolveLenient")
    }

    def showFailuresTask(expression) {
        """
task show {
    def artifacts = configurations.compile.${expression}
    doLast {
        artifacts.collect { true }
    }
}
"""
    }
}
