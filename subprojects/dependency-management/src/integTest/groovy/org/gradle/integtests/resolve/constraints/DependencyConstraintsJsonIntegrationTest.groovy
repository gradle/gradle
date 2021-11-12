/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.constraints

import org.gradle.integtests.fixtures.AbstractPolyglotIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.intellij.lang.annotations.Language

class DependencyConstraintsJsonIntegrationTest extends AbstractPolyglotIntegrationSpec {
    private final String resolveName = 'resolve-json-fixture'

    def setup() {
        buildSpec {
            settings {
                rootProjectName = 'test'
            }
        }
    }

    private ResolveTestFixture createResolveTestFixture(TestFile testDirectory) {
        return new ResolveTestFixture(testDirectory.file("${resolveName}.gradle"), "conf")
            .expectDefaultConfiguration("runtime")
    }

    ResolveTestFixture applySetupToRootProject() {
        def resolve = createResolveTestFixture(testDirectory)
        buildSpec {
            rootProject {
                repositories {
                    maven(mavenRepo.uri)
                }
                configurations {
                    conf
                }
                applyFrom(resolveName)
            }
        }
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        return resolve
    }

    ResolveTestFixture applySetupToBuildSrc() {
        def resolve = createResolveTestFixture(testDirectory.createDir("buildSrc"))
        buildSpec {
            buildSrc {
                rootProject {
                    repositories {
                        maven(mavenRepo.uri)
                    }
                    configurations {
                        conf
                    }
                    applyFrom(resolveName)
                    section(
                        "tasks.named('classes').configure { dependsOn('${resolve.taskName}') }",
                        "tasks.named(\"classes\").configure { dependsOn(\"${resolve.taskName}\") }"
                    )
                }
            }
        }
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        return resolve
    }

    ResolveTestFixture applySetupToIncludedBuild(String includedBuildName) {
        def resolve = createResolveTestFixture(testDirectory.createDir(includedBuildName))
        buildSpec {
            includedBuild(includedBuildName) {
                rootProject {
                    repositories {
                        maven(mavenRepo.uri)
                    }
                    configurations {
                        conf
                    }
                    applyFrom(resolveName)
                }
            }
        }
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        return resolve
    }

    ResolveTestFixture applySetupToNestedIncludedBuild(String parentIncludedBuildName, String childIncludedBuildName) {
        def resolve = createResolveTestFixture(testDirectory.createDir(parentIncludedBuildName).createDir(childIncludedBuildName))
        buildSpec {
            includedBuild(parentIncludedBuildName) {
                includedBuild(childIncludedBuildName) {
                    rootProject {
                        repositories {
                            maven(mavenRepo.uri)
                        }
                        configurations {
                            conf
                        }
                        applyFrom(resolveName)
                    }
                }
            }
        }
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        return resolve
    }

    void "dependency constraint is not included in resolution without a hard dependency"() {
        given:
        def resolve = applySetupToRootProject()

        mavenRepo.module("org", "foo", "1.0").publish()

        writeSpec {
            rootProject {}
            jsonConstraints {
                contents """
{
  "version": "1.0.0",
  "dependencyConstraints": [
    {
      "group": "org",
      "name": "foo",
      "suggestedVersion": "1.1",
      "because": {
        "reason": "Reason"
      }
    }
  ]
}
"""
            }
        }
        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {}
        }
    }

    void "dependency constrain is included into the result of resolution when a hard dependency is also added"() {
        given:
        def resolve = applySetupToRootProject()

        mavenRepo.module("org", "foo", "1.1").publish()

        writeSpec {
            rootProject {
                dependencies {
                    conf 'org:foo'
                }
            }
            jsonConstraints {
                contents """
{
  "version": "1.0.0",
  "dependencyConstraints": [
    {
      "group": "org",
      "name": "foo",
      "suggestedVersion": "1.1",
      "because": {
        "reason": "Reason"
      }
    }
  ]
}
"""
            }
        }

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo", "org:foo:1.1")
                constraint("org:foo:{strictly 1.1}", "org:foo:1.1")
            }
        }
    }

    void "dependency constraint can be used to declare a range of versions to be rejected"() {
        given:
        applySetupToRootProject()

        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "bar", "1.0")
            .dependsOn("org", "foo", "1.1")
            .publish()

        writeSpec {
            rootProject {
                dependencies {
                    conf 'org:bar:1.0'
                }
            }
            jsonConstraints {
                contents """
{
  "version": "1.0.0",
  "dependencyConstraints": [
    {
      "group": "org",
      "name": "foo",
      "suggestedVersion": "1.0",
      "rejectedVersions": [
        "[0.0,1.99]"
      ],
      "because": {
        "reason": "The reason"
      }
    }
  ]
}
"""
            }
        }

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:bar:1.0' (runtime) --> 'org:foo:1.1'
   Constraint path ':test:unspecified' --> 'org:foo:{strictly 1.0; reject [0.0,1.99]}' because of the following reason: The reason""")
    }

    static String jsonConstrain(String group, String name, String suggestedVersion, String rejectedVersion) {
        @Language("json")
        final String json = """
{
  "version": "1.0.0",
  "dependencyConstraints": [
    {
      "group": "$group",
      "name": "$name",
      "suggestedVersion": "$suggestedVersion",
      "rejectedVersions": [
        "$rejectedVersion"
      ],
      "because": {
        "reason": "Reason"
      }
    }
  ]
}
"""
        return json
    }

    static String jsonConstrainOrgFooSuggest_1_1_reject_1_0() {
        return jsonConstrain("org", "foo", "1.1", "1.0")
    }

    def "dependencies constrained in root project influence buildSrc"() {
        given:
        def resolve = applySetupToBuildSrc()

        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()

        writeSpec {
            buildSrc {
                rootProject {
                    dependencies {
                        conf 'org:foo:1.0'
                    }
                }
            }
            jsonConstraints {
                contents jsonConstrainOrgFooSuggest_1_1_reject_1_0()
            }
        }

        when:
        run 'help'

        then:
        resolve.expectGraph {
            root(":buildSrc", ":test-project:") {
                edge("org:foo:1.0", "org:foo:1.1")
                constraint("org:foo:{strictly 1.1; reject 1.0}", "org:foo:1.1")
            }
        }
    }

    def "dependencies constrained in buildSrc only influence buildSrc"() {
        given:
        def rootResolve = applySetupToRootProject()
        def buildSrcResolve = applySetupToBuildSrc()

        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()

        writeSpec {
            buildSrc {
                rootProject {
                    dependencies {
                        conf 'org:foo:1.0'
                    }
                }
                jsonConstraints {
                    contents jsonConstrainOrgFooSuggest_1_1_reject_1_0()
                }
            }
            rootProject {
                dependencies {
                    conf 'org:foo:1.0'
                }
            }
        }

        when:
        run ResolveTestFixture.taskName

        then:
        buildSrcResolve.expectGraph {
            root(":buildSrc", ":test-project:") {
                edge("org:foo:1.0", "org:foo:1.1")
                constraint("org:foo:{strictly 1.1; reject 1.0}", "org:foo:1.1")
            }
        }
        rootResolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0", "org:foo:1.0")
            }
        }
    }

    def "dependencies constrained in an included build only influence the included build"() {
        given:
        def rootResolve = applySetupToRootProject()
        def includedBuildConstrainedName = 'included-a'
        def includedBuildConstrainedResolve = applySetupToIncludedBuild(includedBuildConstrainedName)
        def includedBuildSiblingName = 'included-b'
        def includedBuildSiblingResolve = applySetupToIncludedBuild(includedBuildSiblingName)

        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()

        writeSpec {
            includedBuild(includedBuildConstrainedName) {
                rootProject {
                    dependencies {
                        conf 'org:foo:1.0'
                    }
                }
                jsonConstraints {
                    contents jsonConstrainOrgFooSuggest_1_1_reject_1_0()
                }
            }
            includedBuild(includedBuildSiblingName) {
                rootProject {
                    dependencies {
                        conf 'org:foo:1.0'
                    }
                }
            }
            rootProject {
                dependencies {
                    conf 'org:foo:1.0'
                }
                section(
                    "tasks.named('${ResolveTestFixture.taskName}').configure { dependsOn(gradle.includedBuilds*.task(':${ResolveTestFixture.taskName}')) }",
                    "tasks.named(\"${ResolveTestFixture.taskName}\").configure { dependsOn(gradle.includedBuilds.map{ it.task(\":${ResolveTestFixture.taskName}\") }) }"
                )
            }
        }

        when:
        run ResolveTestFixture.taskName

        then:
        includedBuildConstrainedResolve.expectGraph {
            root(":included-a", ":test-project:") {
                edge("org:foo:1.0", "org:foo:1.1")
                constraint("org:foo:{strictly 1.1; reject 1.0}", "org:foo:1.1")
            }
        }
        includedBuildSiblingResolve.expectGraph {
            root(":included-b", ":test-project:") {
                edge("org:foo:1.0", "org:foo:1.0")
            }
        }
        rootResolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0", "org:foo:1.0")
            }
        }
    }

    def "dependency constraints are a union from parent to child included build"() {
        given:
        executer.startBuildProcessInDebugger(true)
        def rootResolve = applySetupToRootProject()
        def includedBuildParentName = 'included-parent'
        def includedBuildParentResolve = applySetupToIncludedBuild(includedBuildParentName)
        def includedBuildChildName = 'included-child'
        def includedBuildChildResolve = applySetupToNestedIncludedBuild(includedBuildParentName, includedBuildChildName)

        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "buzz", "1.0").publish()
        mavenRepo.module("org", "buzz", "1.1").publish()

        writeSpec {
            // Root constrains foo, parent constrains bar, child constrains buzz
            rootProject {
                dependencies {
                    conf 'org:foo:1.0'
                    conf 'org:bar:1.0'
                    conf 'org:buzz:1.0'
                }
                section(
                    "tasks.named('${ResolveTestFixture.taskName}').configure { dependsOn(gradle.includedBuilds*.task(':${ResolveTestFixture.taskName}')) }",
                    "tasks.named(\"${ResolveTestFixture.taskName}\").configure { dependsOn(gradle.includedBuilds.map{ it.task(\":${ResolveTestFixture.taskName}\") }) }"
                )
                jsonConstraints {
                    contents jsonConstrainOrgFooSuggest_1_1_reject_1_0()
                }
            }
            includedBuild(includedBuildParentName) {
                rootProject {
                    dependencies {
                        conf 'org:foo:1.0'
                        conf 'org:bar:1.0'
                        conf 'org:buzz:1.0'
                    }
                    section(
                        "tasks.named('${ResolveTestFixture.taskName}').configure { dependsOn(gradle.includedBuilds*.task(':${ResolveTestFixture.taskName}')) }",
                        "tasks.named(\"${ResolveTestFixture.taskName}\").configure { dependsOn(gradle.includedBuilds.map{ it.task(\":${ResolveTestFixture.taskName}\") }) }"
                    )
                    jsonConstraints {
                        contents jsonConstrain('org', 'bar', '1.1', '1.0')
                    }
                }

                includedBuild(includedBuildChildName) {
                    rootProject {
                        dependencies {
                            conf 'org:foo:1.0'
                            conf 'org:bar:1.0'
                            conf 'org:buzz:1.0'
                        }
                        jsonConstraints {
                            contents jsonConstrain('org', 'buzz', '1.1', '1.0')
                        }
                    }
                }
            }
        }

        when:
        run ResolveTestFixture.taskName

        then:
        rootResolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0", "org:foo:1.1")
                constraint("org:foo:{strictly 1.1; reject 1.0}", "org:foo:1.1")
                edge("org:bar:1.0", "org:bar:1.0")
                edge("org:buzz:1.0", "org:buzz:1.0")
            }
        }
        includedBuildParentResolve.expectGraph {
            root(":included-parent", ":test-project:") {
                edge("org:foo:1.0", "org:foo:1.1")
                constraint("org:foo:{strictly 1.1; reject 1.0}", "org:foo:1.1")
                edge("org:bar:1.0", "org:bar:1.1")
                constraint("org:bar:{strictly 1.1; reject 1.0}", "org:bar:1.1")
                edge("org:buzz:1.0", "org:buzz:1.0")
            }
        }
        includedBuildChildResolve.expectGraph {
            root(":included-child", ":test-project:") {
                edge("org:foo:1.0", "org:foo:1.1")
                constraint("org:foo:{strictly 1.1; reject 1.0}", "org:foo:1.1")
                edge("org:bar:1.0", "org:bar:1.1")
                constraint("org:bar:{strictly 1.1; reject 1.0}", "org:bar:1.1")
                edge("org:buzz:1.0", "org:buzz:1.1")
                constraint("org:buzz:{strictly 1.1; reject 1.0}", "org:buzz:1.1")
            }
        }
    }

}
