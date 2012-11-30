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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import spock.lang.Ignore

class AliasedArtifactResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    def mavenRepo1 = mavenHttpRepo("maven1")
    def mavenRepo2 = mavenHttpRepo("maven2")
    def ivyRepo1 = ivyHttpRepo("ivy1")
    def ivyRepo2 = ivyHttpRepo("ivy2")

    def "setup"() {
        server.start()

        buildFile << """
            repositories {
                if (project.hasProperty('mavenRepository1')) {
                    maven { url '${mavenRepo1.uri}' }
                } else if (project.hasProperty('mavenRepository2')) {
                    maven { url '${mavenRepo2.uri}' }
                } else if (project.hasProperty('ivyRepository1')) {
                    ivy { url '${ivyRepo1.uri}' }
                } else if (project.hasProperty('ivyRepository2')) {
                    ivy { url '${ivyRepo2.uri}' }
                } else if (project.hasProperty('fileRepository')) {
                    maven { url '${mavenRepo.uri}' }
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

    def "does not re-download maven artifact downloaded from a different maven repository when sha1 matches"() {
        when:
        def projectBRepo1 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.expectPomGet()
        projectBRepo1.getArtifact().expectGet()

        then:
        succeedsWith 'mavenRepository1'

        when:
        def projectBRepo2 = mavenRepo2.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.expectPomHead()
        projectBRepo2.expectPomSha1Get()
        projectBRepo2.expectArtifactHead()
        projectBRepo2.expectArtifactSha1Get()

        then:
        succeedsWith 'mavenRepository2'
    }

    def "does not re-download ivy artifact downloaded from a different ivy repository when sha1 matches"() {
        when:
        def projectBRepo1 = ivyRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.expectIvyGet()
        projectBRepo1.expectJarGet()

        then:
        succeedsWith 'ivyRepository1'

        when:
        def projectBRepo2 = ivyRepo2.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.expectIvyHead()
        projectBRepo2.expectIvySha1Get()
        projectBRepo2.expectJarHead()
        projectBRepo2.expectJarSha1Get()

        then:
        succeedsWith 'ivyRepository2'
    }

    def "does not re-download ivy artifact downloaded from a maven repository when sha1 matches"() {
        when:
        def projectBRepo1 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.expectPomGet()
        projectBRepo1.getArtifact().expectGet()

        then:
        succeedsWith 'mavenRepository1'

        when:
        def projectBRepo2 = ivyRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.expectIvyGet()
        projectBRepo2.expectJarHead()
        projectBRepo2.expectJarSha1Get()

        then:
        succeedsWith 'ivyRepository1'
    }

    def "does not re-download maven artifact downloaded from a ivy repository when sha1 matches"() {
        when:
        def projectBRepo1 = ivyRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.expectIvyGet()
        projectBRepo1.expectJarGet()

        then:
        succeedsWith 'ivyRepository1'

        when:
        def projectBRepo2 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.expectPomGet()
        projectBRepo2.expectArtifactHead()
        projectBRepo2.expectArtifactSha1Get()

        then:
        succeedsWith 'mavenRepository1'
    }

    @Ignore("File repository does not cache artifacts locally, so they are not used to prevent download")
    def "does not download artifact previously accessed from a file uri when sha1 matches"() {
        given:
        succeedsWith 'fileRepository'

        when:
        def projectBRepo2 = mavenRepo2.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.expectPomHead()
        projectBRepo2.expectPomSha1Get()
        projectBRepo2.expectArtifactHead()
        projectBRepo2.expectArtifactSha1Get()

        then:
        succeedsWith 'mavenRepository2'
    }

    def "does re-download maven artifact downloaded from a different URI when sha1 not found"() {
        when:
        def projectBRepo1 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.expectPomGet()
        projectBRepo1.getArtifact().expectGet()

        then:
        succeedsWith 'mavenRepository1'

        when:
        def projectBRepo2 = mavenRepo2.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.expectPomHead()
        projectBRepo2.expectPomSha1GetMissing()
        projectBRepo2.expectPomGet()
        projectBRepo2.expectArtifactHead()
        projectBRepo2.expectArtifactSha1GetMissing()
        projectBRepo2.getArtifact().expectGet()

        then:
        succeedsWith 'mavenRepository2'
    }

    def "does re-download maven artifact downloaded from a different URI when sha1 does not match"() {
        when:
        def projectBRepo1 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.expectPomGet()
        projectBRepo1.getArtifact().expectGet()

        then:
        succeedsWith 'mavenRepository1'

        when:
        def projectBRepo2 = mavenRepo2.module('org.name', 'projectB', '1.0').publishWithChangedContent()
        projectBRepo2.expectPomHead()
        projectBRepo2.expectPomSha1Get()
        projectBRepo2.expectPomGet()
        projectBRepo2.expectArtifactHead()
        projectBRepo2.expectArtifactSha1Get()
        projectBRepo2.getArtifact().expectGet()

        then:
        succeedsWith 'mavenRepository2'
    }

    def succeedsWith(repository) {
        executer.withArguments('-i', "-P${repository}")
        def result = succeeds 'retrieve'
        file('libs').assertHasDescendants('projectB-1.0.jar')
        server.resetExpectations()
        return result
    }
}