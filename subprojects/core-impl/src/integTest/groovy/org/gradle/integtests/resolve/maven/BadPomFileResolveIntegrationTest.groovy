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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Issue

class BadPomFileResolveIntegrationTest extends AbstractDependencyResolutionTest {
    @Issue("http://issues.gradle.org/browse/GRADLE-1005")
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
            task libs << { assert configurations.compile.files.collect {it.name} == ['artifact-1.0.jar'] }
        """

        expect:
        succeeds ":libs"
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2861")
    def "can handle pom with placeholders in dependency management"() {
        given:
        server.start()

        def parent = mavenHttpRepo.module('group', 'parent', '1.0').publish()
        parent.pomFile.text = parent.pomFile.text.replace("</project>", """
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
</project>
""")

        def module = mavenHttpRepo.module('group', 'artifact', '1.0').parent('group', 'parent', '1.0').publish()

        and:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations { compile }
            dependencies {
                compile "group:artifact:1.0"
            }
            task libs << { assert configurations.compile.files.collect {it.name} == ['artifact-1.0.jar'] }
        """

        and:
        parent.pom.expectGet()
        module.pom.expectGet()
        module.artifact.expectGet()

        expect:
        // have to run twice to trigger the failure, to parse the descriptor from the cache
        succeeds ":libs"
        succeeds ":libs"
    }

    public void "reports POM that cannot be parsed"() {
        server.start()
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
task showBroken << { println configurations.compile.files }
"""

        and:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()
        module.pomFile.text = "<project/>"

        when:
        module.pom.expectGet()

        then:
        fails "showBroken"
        failure.assertResolutionFailure(":compile")
            .assertHasCause("Could not parse POM ${module.pom.uri}")
            .assertHasCause("null name not allowed")
    }

    def "reports missing parent POM"() {
        given:
        server.start()

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
task showBroken << { println configurations.compile.files }
"""

        when:
        child.pom.expectGet()
        parent.pom.expectGetMissing()

        // Will always check for a default artifact with a module with 'pom' packaging
        // TODO - should not make this request
        parent.artifact.expectHeadMissing()

        and:
        fails 'showBroken'

        then:
        failure.assertResolutionFailure(':compile')
                .assertHasCause("Could not parse POM ${child.pom.uri}")
                .assertHasCause("Could not find any version that matches org:parent:1.0.")
    }

    def "reports parent POM that cannot be parsed"() {
        given:
        server.start()

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
task showBroken << { println configurations.compile.files }
"""

        when:
        child.pom.expectGet()
        parent.pom.expectGet()

        and:
        fails 'showBroken'

        then:
        failure.assertResolutionFailure(":compile")
            .assertHasCause("Could not parse POM ${child.pom.uri}")
            .assertHasCause("Could not parse POM ${parent.pom.uri}")
            .assertHasCause("null name not allowed")
    }
}