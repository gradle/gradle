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
package org.gradle.integtests.resolve.artifactreuse

import org.gradle.integtests.fixtures.IvyModule
import org.gradle.integtests.fixtures.MavenFileModule
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.junit.Rule
import spock.lang.Ignore

class AliasedArtifactResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    @Rule public final TestResources resources = new TestResources();

    MavenFileModule projectB
    IvyModule ivyProjectB

    def mRepo1 = "/mavenRepo1"
    def mRepo2 = "/mavenRepo2"

    def iRepo1 = "/ivyRepo1"
    def iRepo2 = "/ivyRepo2"

    def "setup"() {
        init()
        projectB = mavenRepo().module('org.name', 'projectB').publish()
        ivyProjectB = ivyRepo().module('org.name', 'projectB').publish()
    }

    def "does not re-download maven artifact downloaded from a different maven repository when sha1 matches"() {
        when:
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWith 'mavenRepository1'

        when:
        projectB.expectPomHead(server, "/mavenRepo2")
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.pom.sha1', projectB.sha1File(projectB.pomFile))
        projectB.expectArtifactHead(server, "/mavenRepo2")
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.jar.sha1', projectB.sha1File(projectB.artifactFile))

        then:
        succeedsWith 'mavenRepository2'
    }

    def "does not re-download ivy artifact downloaded from a different ivy repository when sha1 matches"() {
        when:
        server.expectGet('/ivyRepo1/org.name/projectB/1.0/ivy-1.0.xml', ivyProjectB.ivyFile)
        server.expectGet('/ivyRepo1/org.name/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWith 'ivyRepository1'

        when:
        ivyProjectB.expectIvyHead(server, iRepo2)
        server.expectGet('/ivyRepo2/org.name/projectB/1.0/ivy-1.0.xml.sha1', ivyProjectB.sha1File(ivyProjectB.ivyFile))
        ivyProjectB.expectArtifactHead(server, iRepo2)
        server.expectGet('/ivyRepo2/org.name/projectB/1.0/projectB-1.0.jar.sha1', projectB.sha1File(projectB.artifactFile))

        then:
        succeedsWith 'ivyRepository2'
    }

    def "does not re-download ivy artifact downloaded from a maven repository when sha1 matches"() {
        when:
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWith 'mavenRepository1'

        when:
        server.expectGet('/ivyRepo1/org.name/projectB/1.0/ivy-1.0.xml', ivyProjectB.ivyFile)
        ivyProjectB.expectArtifactHead(server, iRepo1)
        server.expectGet('/ivyRepo1/org.name/projectB/1.0/projectB-1.0.jar.sha1', projectB.sha1File(projectB.artifactFile))

        then:
        succeedsWith 'ivyRepository1'
    }

    def "does not re-download maven artifact downloaded from a ivy repository when sha1 matches"() {
        when:
        server.expectGet('/ivyRepo1/org.name/projectB/1.0/ivy-1.0.xml', ivyProjectB.ivyFile)
        server.expectGet('/ivyRepo1/org.name/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWith 'ivyRepository1'

        when:
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        projectB.expectArtifactHead(server, mRepo1)
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.jar.sha1', projectB.sha1File(projectB.artifactFile))

        then:
        succeedsWith 'mavenRepository1'
    }

    @Ignore("File repository does not cache artifacts locally, so they are not used to prevent download")
    def "does not download artifact previously accessed from a file uri when sha1 matches"() {
        given:
        succeedsWith 'fileRepository'

        when:
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.pom.sha1', projectB.sha1File(projectB.pomFile))
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.jar.sha1', projectB.sha1File(projectB.artifactFile))

        then:
        succeedsWith 'mavenRepository2'
    }

    def "does re-download maven artifact downloaded from a different URI when sha1 not found"() {
        when:
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWith 'mavenRepository1'

        when:
        projectB.expectPomHead(server, "/mavenRepo2")
        server.expectGetMissing('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.pom.sha1')
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        projectB.expectArtifactHead(server, "/mavenRepo2")
        server.expectGetMissing('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.jar.sha1')
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWith 'mavenRepository2'
    }

    def "does re-download maven artifact downloaded from a different URI when sha1 does not match"() {
        when:
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/mavenRepo1/org/name/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWith 'mavenRepository1'

        when:
        projectB.expectPomHead(server, mRepo2)
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.pom.sha1', projectB.md5File(projectB.pomFile))
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        projectB.expectArtifactHead(server, mRepo2)
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.jar.sha1', projectB.md5File(projectB.artifactFile))
        server.expectGet('/mavenRepo2/org/name/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWith 'mavenRepository2'
    }

    private init() {
        server.start()

        buildFile << """
repositories {
    if (project.hasProperty('mavenRepository1')) {
        maven { url 'http://localhost:${server.port}/mavenRepo1' }
    } else if (project.hasProperty('mavenRepository2')) {
        maven { url 'http://localhost:${server.port}/mavenRepo2' }
    } else if (project.hasProperty('ivyRepository1')) {
        ivy { url 'http://localhost:${server.port}/ivyRepo1' }
    } else if (project.hasProperty('ivyRepository2')) {
        ivy { url 'http://localhost:${server.port}/ivyRepo2' }
    } else if (project.hasProperty('fileRepository')) {
        maven { url '${mavenRepo().uri}' }
    }
}
configurations { compile }
dependencies {
    compile 'org.name:projectB:1.0'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
    }

    def succeedsWith(repository) {
        executer.withArguments('-i', "-P${repository}")
        def result = succeeds 'retrieve'
        file('libs').assertHasDescendants('projectB-1.0.jar')
        server.resetExpectations()
        return result
    }
}