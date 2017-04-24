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
import org.gradle.util.TextUtil
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class ResolvedArtifactsApiIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
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
          attribute(usage).compatibilityRules.assumeCompatibleWhenMissing()
          attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
          attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
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
    doLast {
        def artifacts = configurations.compile.${expression}
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
        outputContains("files: [test-lib.jar, a-lib.jar, b-lib.jar, a.jar, test-1.0.jar, b.jar, test2-1.0.jar]")
        outputContains("ids: [test-lib.jar, a-lib.jar, b-lib.jar, a.jar (project :a), test.jar (org:test:1.0), b.jar (project :b), test2.jar (org:test2:1.0)]")
        outputContains("unique ids: [test-lib.jar, a-lib.jar, b-lib.jar, a.jar (project :a), test.jar (org:test:1.0), b.jar (project :b), test2.jar (org:test2:1.0)]")
        outputContains("display-names: [test-lib.jar, a-lib.jar, b-lib.jar, a.jar (project :a), test.jar (org:test:1.0), b.jar (project :b), test2.jar (org:test2:1.0)]")
        outputContains("components: [test-lib.jar, a-lib.jar, b-lib.jar, project :a, org:test:1.0, project :b, org:test2:1.0]")
        outputContains("unique components: [test-lib.jar, a-lib.jar, b-lib.jar, project :a, org:test:1.0, project :b, org:test2:1.0]")
        outputContains("variants: [{artifactType=jar}, {artifactType=jar}, {artifactType=jar}, {artifactType=jar}, {artifactType=jar}, {artifactType=jar}, {artifactType=jar}]")

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
    doLast {
        def artifacts = configurations.compile.${expression}
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
    task oneJar(type: Jar) { baseName = 'a1' }
    task twoJar(type: Jar) { baseName = 'a2' }
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
    task oneJar(type: Jar) { baseName = 'b1' }
    task twoJar(type: Jar) { baseName = 'b2' }
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
    doLast {
        def artifacts = configurations.compile.${expression}
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
    task oneJar(type: Jar) { baseName = 'a1' }
    task twoJar(type: Jar) { baseName = 'a2' }
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
    task oneJar(type: Jar) { baseName = 'b1' }
    task twoJar(type: Jar) { baseName = 'b2' }
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
    doLast {
        def artifacts = configurations.compile.${expression}
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
    task oneJar(type: Jar) { baseName = 'a1' }
    task twoJar(type: Jar) { baseName = 'a2' }
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
    task oneJar(type: Jar) { baseName = 'b1' }
    task twoJar(type: Jar) { baseName = 'b2' }
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
    doLast {
        def artifacts = configurations.compile.${expression}
        println "files: " + artifacts.collect { it.file.name }
        println "ids: " + artifacts.collect { it.id.displayName }
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "variants: " + artifacts.collect { it.variant.attributes }
        assert artifacts.failures.empty
    }
}
"""

        when:
        fails 'show'

        then:
        failure.assertHasCause("""More than one variant matches the consumer attributes:
  - Variant:
      - Found artifactType 'jar' but wasn't required.
      - Found buildType 'debug' but wasn't required.
      - Found flavor 'one' but wasn't required.
      - Required usage 'compile' and found compatible value 'compile'.
  - Variant:
      - Found artifactType 'jar' but wasn't required.
      - Found buildType 'debug' but wasn't required.
      - Found flavor 'two' but wasn't required.
      - Required usage 'compile' and found compatible value 'compile'.""")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
    }

    def "result includes consumer-provided variants"() {
        mavenRepo.module("org", "test", "1.0").publish()

        buildFile << """

class VariantArtifactTransform extends ArtifactTransform {
    List<File> transform(File input) {
        def output = new File(outputDirectory, "transformed-" + input.name)
        output << "transformed"
        return [output]         
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
    registerTransform {
        to.attribute(usage, "transformed")
        artifactTransform(VariantArtifactTransform)
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
    doLast {
        def artifacts = configurations.compile.incoming.artifactView {
            attributes({it.attribute(usage, 'transformed')})
        }.artifacts
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
        outputContains("variants: [{artifactType=jar}, {artifactType=jar, buildType=debug, flavor=one, usage=transformed}, {artifactType=jar, usage=transformed}, {artifactType=jar}]")
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
    doLast {
        def artifacts = configurations.compile.${expression}
        println "files: " + artifacts.collect { rootProject.relativePath(it.file).replace(File.separator, '/') }
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
        outputContains("files: [lib.jar, a/lib.jar, b/lib.jar, a/one/lib.jar, a/two/lib.jar, lib.jar, lib.jar]")
        outputContains("ids: [lib.jar, lib.jar, lib.jar, lib.jar (project :a), lib.jar (project :a), lib.jar (project :a), lib.jar (project :b)]")
        outputContains("unique ids: [lib.jar, lib.jar, lib.jar, lib.jar (project :a), lib.jar (project :a), lib.jar (project :a), lib.jar (project :b)]")
        outputContains("components: [lib.jar, lib.jar, lib.jar, project :a, project :a, project :a, project :b]")
        outputContains("unique components: [lib.jar, lib.jar, lib.jar, project :a, project :b]")

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
    repositories { maven { url '$mavenHttpRepo.uri' } }
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
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not find any matches for org:test:1.0+ as no versions of org:test are available.")
        failure.assertHasCause("Could not resolve org:test2:2.0.")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
        "incoming.artifactView({lenient(true)}).artifacts"            | _
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
        failure.assertHasCause("Could not find test.jar (org:test:1.0).")

        where:
        expression                                                    | _
        "incoming.artifacts"                                          | _
        "incoming.artifactView({}).artifacts"                         | _
        "incoming.artifactView({componentFilter { true }}).artifacts" | _
        "incoming.artifactView({lenient(true)}).artifacts"            | _
    }

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
        "incoming.artifactView({lenient(true)}).artifacts"            | _
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
        failure.assertHasCause("Could not find test.jar (org:test:1.0).")
        failure.assertHasCause("Could not download test2.jar (org:test2:2.0)")
        failure.assertHasCause("broken 1")
        failure.assertHasCause("broken 2")

        where:
        expression                                                    | lenient
        "incoming.artifacts"                                          | false
        "incoming.artifactView({}).artifacts"                         | false
        "incoming.artifactView({componentFilter { true }}).artifacts" | false
        "incoming.artifactView({lenient(true)}).artifacts"            | true
    }

    def "lenient artifact view includes only artifacts that are successfully resolved"() {
        def failureMessage = TextUtil.normaliseLineSeparators("""Could not find missing-artifact.jar (org:missing-artifact:1.0).
Searched in the following locations:
    ${mavenHttpRepo.uri}/org/missing-artifact/1.0/missing-artifact-1.0.jar""")

        buildFile << """
allprojects {
    repositories { maven { url '$mavenHttpRepo.uri' } }
}
dependencies {
    compile 'org:missing-module:1.0'
    compile 'org:missing-artifact:1.0'
    compile 'org:found:2.0'
    compile files('lib.jar')
    compile files { throw new RuntimeException('broken') }
}

task resolveLenient {
    doLast {
        def resolvedFiles = ['lib.jar', 'found-2.0.jar']
        def lenientView = configurations.compile.incoming.artifactView({lenient(true)})
        assert lenientView.files.collect { it.name } == resolvedFiles
        assert lenientView.artifacts.collect { it.file.name } == resolvedFiles
        assert lenientView.artifacts.artifactFiles.collect { it.name } == resolvedFiles
        assert lenientView.artifacts.failures.collect { it.message.replaceAll('\\r\\n', '\\n') } == [
            "Could not resolve all dependencies for configuration ':compile'.",
            "broken",
            '''$failureMessage'''
        ]
    }
}
"""

        given:
        def m0 = mavenHttpRepo.module('org', 'missing-module', '1.0')
        m0.pom.expectGetMissing()
        m0.artifact.expectHeadMissing()

        def m1 = mavenHttpRepo.module('org', 'missing-artifact', '1.0').publish()
        m1.pom.expectGet()
        m1.artifact.expectGetMissing()

        def m2 = mavenHttpRepo.module('org', 'found', '2.0').publish()
        m2.pom.expectGet()
        m2.artifact.expectGet()

        expect:
        succeeds 'resolveLenient'
    }

    def "lenient view includes successfully resolved artifacts and collects failures"() {
        def failureMessage = TextUtil.normaliseLineSeparators("""Could not find test-missing.jar (org:test-missing:1.0).
Searched in the following locations:
    ${mavenHttpRepo.uri}/org/test-missing/1.0/test-missing-1.0.jar""")

        buildFile << """
allprojects {
    repositories { maven { url '$mavenHttpRepo.uri' } }
}
dependencies {
    compile 'org:test:1.0'
    compile 'org:test-missing:1.0'
    compile 'org:test-bad:2.0'
    compile files { throw new RuntimeException('broken 1') }
    compile files { throw new RuntimeException('broken 2') }
}

task resolve {
    doLast {
        def view = configurations.compile.incoming.artifactView({lenient(true)})
        assert view.files.collect { it.name } == ['test-1.0.jar']
        def artifactCollection = view.artifacts
        assert artifactCollection.artifacts.collect { it.id.displayName } == ['test.jar (org:test:1.0)']
        assert artifactCollection.failures.collect { it.message.replaceAll('\\r\\n', '\\n') } == [
            'broken 1',
            'broken 2',
            '''$failureMessage''',
            '''Could not download test-bad.jar (org:test-bad:2.0)'''
        ]
    }
}
"""

        when:
        def ok = mavenHttpRepo.module("org", "test", "1.0").publish()
        ok.pom.expectGet()
        ok.artifact.expectGet()
        def missing = mavenHttpRepo.module('org', 'test-missing', '1.0').publish()
        missing.pom.expectGet()
        missing.artifact.expectGetMissing()
        def bad = mavenHttpRepo.module('org', 'test-bad', '2.0').publish()
        bad.pom.expectGet()
        2.times {
            bad.artifact.expectGetBroken()
        }

        then:
        succeeds 'resolve'
    }

    def showFailuresTask(expression) {
        """
task show {
    doLast {
        def artifacts = configurations.compile.${expression}
        artifacts.collect { true }
        // If lenient, need to rethrow
        throw new org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration.ArtifactResolveException('artifacts', 'artifacts', "configuration ':compile'", artifacts.failures as List)
    }
}
"""
    }
}
