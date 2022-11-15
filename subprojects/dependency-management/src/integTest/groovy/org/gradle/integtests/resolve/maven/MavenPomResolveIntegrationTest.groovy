/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

class MavenPomResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "follows relocation to another group"() {
        given:
        def original = mavenHttpRepo.module("groupA", "projectA", "1.2").publishPom()
        original.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>projectA</artifactId>
    <version>1.2</version>
    <distributionManagement>
        <relocation>
            <groupId>newGroupA</groupId>
        </relocation>
    </distributionManagement>
</project>
"""

        def newModule = mavenHttpRepo.module("newGroupA", "projectA", "1.2").publish()
        newModule.publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:projectA:1.2' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and:
        original.pom.expectGet()
        newModule.pom.expectGet()
        newModule.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("libs").assertHasDescendants("projectA-1.2.jar")
    }


    @Issue("https://issues.gradle.org/browse/GRADLE-2861")
    @ToBeFixedForConfigurationCache
    def "can handle pom with unspecified properties in dependency management"() {
        given:
        def parent = mavenHttpRepo.module('group', 'parent', '1.0').publish()
        parent.pomFile.text = parent.pomFile.text.replace("</project>", '''
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>${some.group}</groupId>
            <artifactId>${some.artifact}</artifactId>
            <version>${some.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
</project>
''')

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
            task libs { doLast { assert configurations.compile.files.collect {it.name} == ['artifact-1.0.jar'] } }
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

    @Issue("https://github.com/gradle/gradle/issues/22279")
    def "can resolve POM as an artifact after a relocation"() {
        given:
        def original = mavenHttpRepo.module('groupA', 'artifactA', '1.0').publishPom()
        original.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.0</version>
    <distributionManagement>
        <relocation>
            <groupId>groupA</groupId>
            <artifactId>artifactA</artifactId>
            <version>2.0</version>
        </relocation>
    </distributionManagement>
</project>
"""

        def newModule = mavenHttpRepo.module('groupA', 'artifactA', '2.0').publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { 
    first
    second
}
dependencies { 
    first 'groupA:artifactA:1.0' 
    second 'groupA:artifactA:2.0@pom' 
}

task retrieve {
    doLast {
        // populates cache for POM artifact in memory
        configurations.first.resolve()
        
        def resolvedArtifacts = configurations.second.resolvedConfiguration.resolvedArtifacts
        assert resolvedArtifacts.size() == 1
        
        def resolvedArtifact = resolvedArtifacts[0]
        assert resolvedArtifact.moduleVersion.id.group == "groupA"
        assert resolvedArtifact.moduleVersion.id.name == "artifactA"
        assert resolvedArtifact.moduleVersion.id.version == "2.0"       
    }
}

"""

        and:
        original.pom.expectGet()
        newModule.pom.expectGet()

        expect:
        succeeds "retrieve"
    }

    @Issue("https://github.com/gradle/gradle/issues/22279")
    def "can resolve POM as an artifact after it was resolved via ARQ"() {
        given:
        def original = mavenHttpRepo.module('groupA', 'artifactA', '1.0').publishPom()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { 
    conf
}
dependencies { 
    conf 'groupA:artifactA:1.0@pom' 
}

task retrieve {
    doLast {
        // populates cache for POM artifact in memory
        def result = dependencies.createArtifactResolutionQuery()
                                    .forModule("groupA", "artifactA", "1.0")
                                    .withArtifacts(MavenModule, MavenPomArtifact)
                                    .execute()
        for (component in result.resolvedComponents) {
            component.getArtifacts(MavenPomArtifact).each { println "POM for " + component }
        }

        def resolvedArtifacts = configurations.conf.resolvedConfiguration.resolvedArtifacts
        assert resolvedArtifacts.size() == 1
        
        def resolvedArtifact = resolvedArtifacts[0]
        assert resolvedArtifact.moduleVersion.id.group == "groupA"
        assert resolvedArtifact.moduleVersion.id.name == "artifactA"
        assert resolvedArtifact.moduleVersion.id.version == "1.0"       
    }
}

"""

        and:
        original.pom.expectGet()

        expect:
        succeeds "retrieve"
    }
}
