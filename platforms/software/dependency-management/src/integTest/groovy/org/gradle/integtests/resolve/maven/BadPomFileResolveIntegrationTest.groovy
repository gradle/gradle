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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveFailureTestFixture
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class BadPomFileResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    final resolve = new ResolveTestFixture(buildFile, "compile")
    final failedResolve = new ResolveFailureTestFixture(buildFile, "compile")

    def setup() {
        settingsFile """
            rootProject.name = 'test'
        """
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-1005")
    def "can handle self referencing dependency"() {
        given:
        mavenRepo().module('group', 'artifact', '1.0').dependsOn('group', 'artifact', '1.0').publish()

        and:
        buildFile << """
            repositories {
                maven { url "${mavenRepo().uri}" }
            }
            configurations { compile }
            dependencies {
                compile "group:artifact:1.0"
            }
        """
        resolve.prepare()

        expect:
        succeeds ":checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:artifact:1.0")
            }
        }
    }

    def "reports POM that cannot be parsed"() {
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
"""
        failedResolve.prepare()

        and:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()
        module.pomFile.text = "<project/>"

        when:
        module.pom.expectGet()

        then:
        fails "checkDeps"
        failedResolve.assertFailurePresent(failure)
        failure.assertResolutionFailure(":compile")
            .assertHasCause("Could not parse POM ${module.pom.uri}")
            .assertHasCause("Missing required attribute: groupId")
    }

    def "reports local POM that cannot be parsed"() {
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
"""
        failedResolve.prepare()

        and:
        def module = mavenRepo.module('group', 'projectA', '1.2').publish()
        module.pomFile.text = "<project/>"

        when:
        fails "checkDeps"

        then:
        failedResolve.assertFailurePresent(failure)
        failure.assertResolutionFailure(":compile")
            .assertHasCause("Could not parse POM ${module.pomFile}")
            .assertHasCause("Missing required attribute: groupId")
    }

    def "reports missing parent POM"() {
        given:
        def parent = mavenHttpRepo.module("org", "parent", "1.0")

        def child = mavenHttpRepo.module("org", "child", "1.0")
        child.parent("org", "parent", "1.0")
        child.publish()

        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
"""
        failedResolve.prepare()

        when:
        child.pom.expectGet()
        parent.pom.expectGetMissing()

        and:
        fails 'checkDeps'

        then:
        failedResolve.assertFailurePresent(failure)
        failure.assertResolutionFailure(':compile')
                .assertHasCause("Could not parse POM ${child.pom.uri}")
                .assertHasCause("""Could not find org:parent:1.0.
Searched in the following locations:
  - ${parent.pom.uri}""")
    }

    def "reports parent POM that cannot be parsed"() {
        given:
        def parent = mavenHttpRepo.module("org", "parent", "1.0").publish()
        parent.pomFile.text = "<project/>"

        def child = mavenHttpRepo.module("org", "child", "1.0")
        child.parent("org", "parent", "1.0")
        child.publish()

        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
"""
        failedResolve.prepare()

        when:
        child.pom.expectGet()
        parent.pom.expectGet()

        and:
        fails 'checkDeps'

        then:
        failedResolve.assertFailurePresent(failure)
        failure.assertResolutionFailure(":compile")
            .assertHasCause("Could not parse POM ${child.pom.uri}")
            .assertHasCause("Could not parse POM ${parent.pom.uri}")
            .assertHasCause("Missing required attribute: groupId")
    }

    def "reports failure to parse POM due to missing dependency #attribute attribute"() {
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
"""
        failedResolve.prepare()

        and:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').dependsOn("other-groupId", "other-artifactId", "1.0").publish()
        def elementToReplace = "<$attribute>other-$attribute</$attribute>"
        module.pomFile.text = module.pomFile.text.replace(elementToReplace, "<!--$elementToReplace-->")

        when:
        fails "checkDeps"

        then:
        failedResolve.assertFailurePresent(failure)
        failure.assertResolutionFailure(":compile")
            .assertHasCause("Could not parse POM ${module.pomFile}")
            .assertHasCause("Missing required attribute: dependency $attribute")

        where:
        attribute << ["groupId", "artifactId"]
    }

    @Issue("https://github.com/gradle/gradle/issues/23208")
    @Issue("https://github.com/gradle/gradle/issues/3065")
    def "handles broken packaging type gracefully"() {
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
"""
        resolve.prepare()

        and:
        def projectA = mavenHttpRepo.module('group', 'projectA', '1.2').publish()
        projectA.pomFile.text = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>projectA</artifactId>
    <version>1.2</version>
    <packaging>\${package.type}</packaging>
</project>
"""

        when:
        projectA.pom.expectGet()
        projectA.artifact.expectGet()

        then:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:projectA:1.2") {
                    artifact(fileName: "projectA-1.2.jar", type: "jar")
                }
            }
        }
    }
}
