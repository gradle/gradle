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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class AliasedArtifactResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def mavenRepo1 = mavenHttpRepo("maven1")
    def mavenRepo2 = mavenHttpRepo("maven2")
    def ivyRepo1 = ivyHttpRepo("ivy1")
    def ivyRepo2 = ivyHttpRepo("ivy2")

    def setup() {
        buildFile << """
            repositories {
                if (project.hasProperty('mavenRepository1')) {
                    maven { url = '${mavenRepo1.uri}' }
                } else if (project.hasProperty('mavenRepository2')) {
                    maven { url = '${mavenRepo2.uri}' }
                } else if (project.hasProperty('ivyRepository1')) {
                    ivy { url = "${ivyRepo1.uri}" }
                } else if (project.hasProperty('ivyRepository2')) {
                    ivy { url = "${ivyRepo2.uri}" }
                } else if (project.hasProperty('fileRepository')) {
                    maven { url = '${mavenRepo.uri}' }
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
        def projectBModuleRepo1 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBModuleRepo1.pom.expectGet()
        projectBModuleRepo1.artifact.expectGet()

        then:
        succeedsWith 'mavenRepository1'

        when:
        def projectBModuleRepo2 = mavenRepo2.module('org.name', 'projectB', '1.0').publish()
        projectBModuleRepo2.pom.expectHead()
        projectBModuleRepo2.pom.sha1.expectGet()
        projectBModuleRepo2.artifact.expectHead()
        projectBModuleRepo2.artifact.sha1.expectGet()

        then:
        succeedsWith 'mavenRepository2'
    }

    def "does not re-download ivy artifact downloaded from a different ivy repository when sha1 matches"() {
        when:
        def projectBRepo1 = ivyRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.ivy.expectGet()
        projectBRepo1.jar.expectGet()

        then:
        succeedsWith 'ivyRepository1'

        when:
        def projectBRepo2 = ivyRepo2.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.ivy.expectHead()
        projectBRepo2.ivy.sha1.expectGet()
        projectBRepo2.jar.expectHead()
        projectBRepo2.jar.sha1.expectGet()

        then:
        succeedsWith 'ivyRepository2'
    }

    def "does not re-download ivy artifact downloaded from a maven repository when sha1 matches"() {
        when:
        def projectBRepo1 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.pom.expectGet()
        projectBRepo1.artifact.expectGet()

        then:
        succeedsWith 'mavenRepository1'

        when:
        def projectBRepo2 = ivyRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.ivy.expectGet()
        projectBRepo2.jar.expectHead()
        projectBRepo2.jar.sha1.expectGet()

        then:
        succeedsWith 'ivyRepository1'
    }

    def "does not re-download maven artifact downloaded from a ivy repository when sha1 matches"() {
        when:
        def projectBRepo1 = ivyRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.ivy.expectGet()
        projectBRepo1.jar.expectGet()

        then:
        succeedsWith 'ivyRepository1'

        when:
        def projectBRepo2 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.pom.expectGet()
        projectBRepo2.artifact.expectHead()
        projectBRepo2.artifact.sha1.expectGet()

        then:
        succeedsWith 'mavenRepository1'
    }

    def "does re-download maven artifact downloaded from a different URI when sha1 not found"() {
        when:
        def projectBRepo1 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.pom.expectGet()
        projectBRepo1.artifact.expectGet()

        then:
        succeedsWith 'mavenRepository1'

        when:
        def projectBRepo2 = mavenRepo2.module('org.name', 'projectB', '1.0').publish()
        projectBRepo2.pom.expectHead()
        projectBRepo2.pom.sha1.expectGetMissing()
        projectBRepo2.pom.expectGet()
        projectBRepo2.artifact.expectHead()
        projectBRepo2.artifact.sha1.expectGetMissing()
        projectBRepo2.artifact.expectGet()

        then:
        succeedsWith 'mavenRepository2'
    }

    def "does re-download maven artifact downloaded from a different URI when sha1 does not match"() {
        when:
        def projectBRepo1 = mavenRepo1.module('org.name', 'projectB', '1.0').publish()
        projectBRepo1.pom.expectGet()
        projectBRepo1.artifact.expectGet()

        then:
        succeedsWith 'mavenRepository1'

        when:
        def projectBRepo2 = mavenRepo2.module('org.name', 'projectB', '1.0').publishWithChangedContent()
        projectBRepo2.pom.expectHead()
        projectBRepo2.pom.sha1.expectGet()
        projectBRepo2.pom.expectGet()

        projectBRepo2.artifact.expectHead()
        projectBRepo2.artifact.sha1.expectGet()
        projectBRepo2.artifact.expectGet()

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
