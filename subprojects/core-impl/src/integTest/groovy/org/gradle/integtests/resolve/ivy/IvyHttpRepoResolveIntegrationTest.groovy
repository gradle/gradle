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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.junit.Rule

class IvyHttpRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule ProgressLoggingFixture progressLogger = new ProgressLoggingFixture(executer, temporaryFolder)

    public void "can resolve and cache dependencies from an HTTP Ivy repository"() {
        server.start()
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    ivy { url "${ivyHttpRepo.uri}" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""
        when:
        module.expectIvyGet()
        module.expectJarGet()

        then:
        succeeds 'listJars'
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo/group/projectA/1.2/ivy-1.2.xml")
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo/group/projectA/1.2/projectA-1.2.jar")

        when:
        server.resetExpectations()
        then:
        succeeds 'listJars'
    }

    public void "can resolve and cache artifact-only dependencies from an HTTP Ivy repository"() {
        server.start()
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    ivy { url "${ivyHttpRepo.uri}" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2@jar' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""


        when:
        module.expectIvyGet()
        module.expectJarGet()

        then:
        executer.withArgument("-i")
        succeeds('listJars')

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies

        then:
        executer.withArgument("-i")
        succeeds('listJars')
    }

    public void "can resolve and cache dependencies from multiple HTTP Ivy repositories"() {
        server.start()
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def moduleA = repo1.module('group', 'projectA').publish()
        def missingModuleB = repo1.module('group', 'projectB')
        def moduleB = repo2.module('group', 'projectB').publish()
        def brokenModuleC = repo1.module('group', 'projectC')
        def moduleC = repo2.module('group', 'projectC').publish()

        and:
        buildFile << """
repositories {
    ivy { url "${repo1.uri}" }
    ivy { url "${repo2.uri}" }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0', 'group:projectB:1.0', 'group:projectC:1.0'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar', 'projectB-1.0.jar', 'projectC-1.0.jar']
}
"""

        when:
        moduleA.expectIvyGet()
        moduleA.expectJarGet()

        // Handles missing in repo1
        missingModuleB.expectIvyGetMissing()
        missingModuleB.expectJarHeadMissing()

        moduleB.expectIvyGet()
        moduleB.expectJarGet()

        // Handles from broken url in repo1 (but does not cache)
        brokenModuleC.expectIvyGetBroken()

        moduleC.expectIvyGet()
        moduleC.expectJarGet()

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        // Will always re-attempt a broken repository
        brokenModuleC.expectIvyHeadBroken()
        // No extra calls for cached dependencies

        then:
        succeeds('listJars')
    }

    public void "uses all configured patterns to resolve artifacts and caches result"() {
        server.start()

        given:
        def module = ivyRepo().module('group', 'projectA', '1.2').publish()

        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}/first"
        artifactPattern "http://localhost:${server.port}/second/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
        artifactPattern "http://localhost:${server.port}/third/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
        ivyPattern "http://localhost:${server.port}/second/[module]/[revision]/ivy.xml"
        ivyPattern "http://localhost:${server.port}/third/[module]/[revision]/ivy.xml"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task show << { println configurations.compile.files }
"""

        when:
        server.expectGetMissing('/first/group/projectA/1.2/ivy-1.2.xml')
        server.expectGetMissing('/first/group/projectA/1.2/projectA-1.2.jar')
        server.expectGetMissing('/second/projectA/1.2/ivy.xml')
        server.expectGetMissing('/second/projectA/1.2/projectA-1.2.jar')
        server.expectGet('/third/projectA/1.2/ivy.xml', module.ivyFile)
        server.expectGet('/third/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('show')

        when:
        server.resetExpectations()

        then:
        succeeds('show')
    }

    public void "replaces org.name with org/name when using maven layout"() {
        server.start()

        given:
        def module = ivyRepo().module('org.name.here', 'projectA', '1.2').publish()

        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
        layout "maven"
    }
}
configurations { compile }
dependencies {
    compile 'org.name.here:projectA:1.2'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        server.expectGet('/org/name/here/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/org/name/here/projectA/1.2/projectA-1.2.jar', module.jarFile)

        and:
        succeeds('retrieve')

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    def "reuses cached details when switching ivy resolve mode"() {
        given:
        server.start()
        buildFile << """
configurations {
    compile
}
dependencies {
    repositories {
        ivy {
            url "${ivyHttpRepo.uri}"
            resolve.dynamicMode = project.hasProperty('useDynamicResolve')
        }
    }
    compile 'org:projectA:1.2'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""
        def moduleA = ivyHttpRepo.module('org', 'projectA', '1.2')
                .dependsOn(organisation: 'org', module: 'projectB', revision: '1.5', revConstraint: 'latest.integration')
                .publish()

        def moduleB15 = ivyHttpRepo.module('org', 'projectB', '1.5')
                .publish()

        def moduleB16 = ivyHttpRepo.module('org', 'projectB', '1.6')
                .publish()

        when:
        moduleA.expectIvyGet()
        moduleA.expectJarGet()
        moduleB15.expectIvyGet()
        moduleB15.expectJarGet()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.5.jar')

        when:
        server.resetExpectations()
        ivyHttpRepo.expectDirectoryListGet('org', 'projectB')
        moduleB16.expectIvyGet()
        moduleB16.expectJarGet()
        executer.withArguments("-PuseDynamicResolve=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.6.jar')

        when:
        server.resetExpectations()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.5.jar')

        when:
        server.resetExpectations()
        executer.withArguments("-PuseDynamicResolve=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.6.jar')
    }
}
