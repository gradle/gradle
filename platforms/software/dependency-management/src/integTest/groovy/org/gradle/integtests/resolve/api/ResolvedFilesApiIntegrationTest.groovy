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

class ResolvedFilesApiIntegrationTest extends AbstractHttpDependencyResolutionTest {

    // These methods return Set<File>, and do not carry task dependencies
    private static final List<String> FILE_EXPRESSION_LIST = [
        "configurations.compile.files",
        "configurations.compile.resolve()",
    ]

    // These methods return FileCollection, and are expected to track task dependencies
    private static final List<String> FILE_COLLECTION_EXPRESSION_LIST = [
        "configurations.compile",
        "configurations.compile.incoming.files",
        "configurations.compile.incoming.artifacts.artifactFiles",
        "configurations.compile.incoming.artifactView { }.files",
        "configurations.compile.incoming.artifactView { }.artifacts.artifactFiles",
        "configurations.compile.incoming.artifactView { componentFilter { true } }.files",
        "configurations.compile.incoming.artifactView { componentFilter { true } }.artifacts.artifactFiles",
    ]

    private static final List<String> ALL_EXPRESSIONS = FILE_EXPRESSION_LIST + FILE_COLLECTION_EXPRESSION_LIST

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    String getHeader() {
        """
            def usage = Attribute.of('usage', String)
            dependencies {
                attributesSchema {
                   attribute(usage)
                }
            }
            configurations {
                compile {
                    attributes.attribute(usage, 'compile')
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "result includes files from local and external components and file dependencies in a fixed order"() {
        mavenRepo.module("org", "test", "1.0").publish()
        mavenRepo.module("org", "test2", "1.0").publish()

        settingsFile << """
            include 'a', 'b'
            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """

        buildFile << """
            $header

            dependencies {
                compile files('test-lib.jar')
                compile project(':a')
                compile 'org:test:1.0'
                artifacts {
                    compile file('test.jar')
                }
            }

            task show {
                doLast {
                    println "files: " + ${expression}.collect { it.name }
                }
            }
        """

        file("a/build.gradle") << """
            $header

            dependencies {
                compile files('a-lib.jar')
                compile project(':b')
                compile 'org:test:1.0'
            }
            artifacts {
                compile file('a.jar')
            }
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile files('b-lib.jar')
                compile 'org:test2:1.0'
            }
            artifacts {
                compile file('b.jar')
            }
        """

        when:
        run 'show'

        then:
        outputContains("files: [test-lib.jar, a.jar, a-lib.jar, test-1.0.jar, b.jar, b-lib.jar, test2-1.0.jar")

        where:
        expression << ALL_EXPRESSIONS
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "applies compatibility rules to select variant"() {
        settingsFile << """
            include 'a', 'b'
        """

        def common = """
            $header

            def flavor = Attribute.of('flavor', String)

            dependencies {
                attributesSchema.attribute(flavor)
            }
        """

        buildFile << """
            $common

            configurations {
                compile.attributes.attribute(flavor, 'preview')
            }

            dependencies {
                compile project(':a')
            }

            task show {
                inputs.files ${expression}
                doLast {
                    println "files: " + ${expression}.collect { it.name }
                }
            }
        """

        file("a/build.gradle") << """
            $common

            class FreeRule implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    if (details.consumerValue == 'preview' && details.producerValue == 'free') {
                        details.compatible()
                    }
                }
            }
            dependencies {
                attributesSchema.attribute(flavor) {
                    compatibilityRules.add(FreeRule)
                }
                compile project(':b')
            }
            ${freeAndPaidFlavoredJars('a')}
        """

        file("b/build.gradle") << """
            $common

            class PaidRule implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    if (details.consumerValue == 'preview' && details.producerValue == 'paid') {
                        details.compatible()
                    }
                }
            }
            dependencies {
                attributesSchema.attribute(flavor) {
                    compatibilityRules.add(PaidRule)
                }
            }
            ${freeAndPaidFlavoredJars('b')}
        """

        expect:
        succeeds("show")
        output.contains("files: [a-free.jar, b-paid.jar]")
        if (FILE_COLLECTION_EXPRESSION_LIST.contains(expression)) {
            result.assertTasksExecuted(':a:freeJar', ':b:paidJar', ':show')
        }

        where:
        expression << ALL_EXPRESSIONS
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "applies disambiguation rules to select variant"() {
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            $header

            def flavor = Attribute.of('flavor', String)

            dependencies {
                compile project(':a')
            }

            task show {
                inputs.files ${expression}
                doLast {
                    println "files: " + ${expression}.collect { it.name }
                }
            }
        """

        file("a/build.gradle") << """
            $header

            def flavor = Attribute.of('flavor', String)

            class SelectFreeRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    details.closestMatch('free')
                }
            }
            dependencies {
                attributesSchema.attribute(flavor) {
                    disambiguationRules.add(SelectFreeRule)
                }
                compile project(':b')
            }
            ${freeAndPaidFlavoredJars('a')}
        """

        file("b/build.gradle") << """
            $header

            def flavor = Attribute.of('flavor', String)

            class SelectPaidRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    details.closestMatch('paid')
                }
            }
            dependencies {
                    attributesSchema.attribute(flavor) {
                    disambiguationRules.add(SelectPaidRule)
                }
            }
            ${freeAndPaidFlavoredJars('b')}
        """

        expect:
        succeeds("show")
        output.contains("files: [a-free.jar, b-paid.jar]")
        if (FILE_COLLECTION_EXPRESSION_LIST.contains(expression)) {
            result.assertTasksExecuted(':a:freeJar', ':b:paidJar', ':show')
        }

        where:
        expression << ALL_EXPRESSIONS
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "reports failure when there is more than one compatible variant"() {
        settingsFile << """
            include 'a', 'b'
        """

        buildFile << """
            $header

            def flavor = Attribute.of('flavor', String)

            dependencies {
                compile project(':a')
            }

            task show {
                doLast {
                    println "files: " + ${expression}.collect { it.name }
                }
            }
        """

        file("a/build.gradle") << """
            $header

            def flavor = Attribute.of('flavor', String)

            dependencies {
                attributesSchema.attribute(flavor)
                compile project(':b')
            }
            ${freeAndPaidFlavoredJars('a')}
        """

        file("b/build.gradle") << """
            $header

            def flavor = Attribute.of('flavor', String)

            dependencies {
                attributesSchema.attribute(flavor)
            }
            ${freeAndPaidFlavoredJars('b')}
        """

        expect:
        fails("show")
        failure.assertHasCause("""The consumer was configured to find attribute 'usage' with value 'compile'. However we cannot choose between the following variants of project :a:
  - Configuration ':a:compile' variant free declares attribute 'usage' with value 'compile':
      - Unmatched attributes:
          - Provides artifactType 'jar' but the consumer didn't ask for it
          - Provides flavor 'free' but the consumer didn't ask for it
  - Configuration ':a:compile' variant paid declares attribute 'usage' with value 'compile':
      - Unmatched attributes:
          - Provides artifactType 'jar' but the consumer didn't ask for it
          - Provides flavor 'paid' but the consumer didn't ask for it""")

        where:
        expression << ALL_EXPRESSIONS
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "reports failure when there is no compatible variant"() {
        mavenRepo.module("test", "test", "1.2").publish()

        settingsFile << """
            include 'a', 'b'
            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """

        def common = """
            $header
            def flavor = Attribute.of('flavor', String)

            dependencies.attributesSchema.attribute(flavor)
        """

        buildFile << """
            $common

            dependencies {
                compile project(':a')
            }

            configurations.compile {
                attributes.attribute(flavor, 'preview')
                attributes.attribute(Attribute.of('artifactType', String), 'dll')
            }

            task show {
                doLast {
                    println "files: " + ${expression}.collect { it.name }
                }
            }
        """

        file("a/build.gradle") << """
            $common

            dependencies {
                compile project(':b')
                compile 'test:test:1.2'
                compile files('things.jar')
            }
            ${freeAndPaidFlavoredJars('a')}
        """

        file("b/build.gradle") << """
            $common

            ${freeAndPaidFlavoredJars('b')}
        """

        expect:
        fails("show")
        failure.assertHasCause("""No variants of project :a match the consumer attributes:
  - Configuration ':a:compile' variant free declares attribute 'usage' with value 'compile':
      - Incompatible because this component declares attribute 'artifactType' with value 'jar', attribute 'flavor' with value 'free' and the consumer needed attribute 'artifactType' with value 'dll', attribute 'flavor' with value 'preview'
  - Configuration ':a:compile' variant paid declares attribute 'usage' with value 'compile':
      - Incompatible because this component declares attribute 'artifactType' with value 'jar', attribute 'flavor' with value 'paid' and the consumer needed attribute 'artifactType' with value 'dll', attribute 'flavor' with value 'preview'""")

        failure.assertHasCause("""No variants of test:test:1.2 match the consumer attributes:
  - test:test:1.2 configuration default:
      - Incompatible because this component declares attribute 'artifactType' with value 'jar' and the consumer needed attribute 'artifactType' with value 'dll'
      - Other compatible attributes:
          - Doesn't say anything about flavor (required 'preview')
          - Doesn't say anything about usage (required 'compile')""")

        failure.assertHasCause("""No variants of things.jar match the consumer attributes:
  - things.jar:
      - Incompatible because this component declares attribute 'artifactType' with value 'jar' and the consumer needed attribute 'artifactType' with value 'dll'
      - Other compatible attributes:
          - Doesn't say anything about flavor (required 'preview')
          - Doesn't say anything about usage (required 'compile')""")

        where:
        expression << ALL_EXPRESSIONS
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "reports failure to resolve component when files are queried using #expression"() {
        settingsFile << """
            dependencyResolutionManagement {
                repositories { maven { url = '$mavenHttpRepo.uri' } }
            }
        """

        buildFile << """
            $header

            dependencies {
                compile 'org:test:1.0+'
                compile 'org:test2:2.0'
            }

            task show {
                doLast {
                    ${expression}.collect { it.name }
                }
            }
        """

        given:
        mavenHttpRepo.getModuleMetaData('org', 'test').expectGetMissing()
        def m = mavenHttpRepo.module('org', 'test2', '2.0').publish()
        m.pom.expectGetBroken()

        when:
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Could not find any matches for org:test:1.0+ as no versions of org:test are available.")
        failure.assertHasCause("Could not resolve org:test2:2.0.")

        where:
        expression << ALL_EXPRESSIONS
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "reports failure to download artifact when files are queried using #expression"() {
        settingsFile << """
            dependencyResolutionManagement {
                repositories { maven { url = '$mavenHttpRepo.uri' } }
            }
        """

        buildFile << """
            $header

            dependencies {
                compile 'org:test:1.0'
                compile 'org:test2:2.0'
            }

            task show {
                doLast {
                    ${expression}.collect { it.name }
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
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Could not find test-1.0.jar (org:test:1.0).")

        where:
        expression << ALL_EXPRESSIONS
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "reports failure to query file dependency when files are queried using #expression"() {
        buildFile << """
            $header

            dependencies {
                compile files { throw new RuntimeException('broken') }
                compile files('lib.jar')
            }

            task show {
                doLast {
                    ${expression}.collect { it.name }
                }
            }
        """

        when:\
        fails 'show'

        then:
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("broken")

        where:
        expression << ALL_EXPRESSIONS
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "reports multiple failures to resolve artifacts when files are queried using #expression"() {
        settingsFile << """
            include 'a'
            dependencyResolutionManagement {
                repositories { maven { url = '$mavenHttpRepo.uri' } }
            }
        """
        buildFile << """
            $header

            dependencies {
                compile 'org:test:1.0'
                compile 'org:test2:2.0'
                compile files { throw new RuntimeException('broken 1') }
                compile files { throw new RuntimeException('broken 2') }
                compile project(':a')
            }

            task show {
                doLast {
                    ${expression}.collect { it.name }
                }
            }
        """

        file("a/build.gradle") << """
            $header

            configurations.compile.outgoing.variants {
                v1 { }
                v2 { }
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
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Could not find test-1.0.jar (org:test:1.0).")
        failure.assertHasCause("Could not download test2-2.0.jar (org:test2:2.0)")
        failure.assertHasCause("broken 1")
        failure.assertHasCause("broken 2")
        failure.assertHasCause("The consumer was configured to find attribute 'usage' with value 'compile'. However we cannot choose between the following variants of project :a:")

        where:
        expression << ALL_EXPRESSIONS
    }

    private String freeAndPaidFlavoredJars(String prefix) {
        """
            task freeJar(type: Jar) { archiveFileName = '$prefix-free.jar' }
            task paidJar(type: Jar) { archiveFileName = '$prefix-paid.jar' }
            tasks.withType(Jar) { destinationDirectory = buildDir }
            configurations.compile.outgoing.variants {
                free {
                    attributes.attribute(flavor, 'free')
                    artifact freeJar
                }
                paid {
                    attributes.attribute(flavor, 'paid')
                    artifact paidJar
                }
            }
        """
    }
}
