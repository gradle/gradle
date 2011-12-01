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

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.gradle.util.SetSystemProperties
import org.hamcrest.Matchers
import org.junit.Rule
import static org.hamcrest.Matchers.containsString

class IvyRemoteDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()
    @Rule
    public SetSystemProperties systemProperties = new SetSystemProperties()

    def "setup"() {
        requireOwnUserHomeDir()
    }

    public void "can resolve and cache dependencies from an HTTP Ivy repository"() {
        server.start()
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""
        when:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds 'listJars'

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies

        then:
        succeeds 'listJars'
    }

    public void "can resolve and cache artifact-only dependencies from an HTTP Ivy repository"() {
        server.start()
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2@jar' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""


        when:
        // TODO: Should meta-data be fetched for an artifact-only dependency?
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies

        then:
        succeeds('listJars')
    }

    public void "can resolve and cache dependencies from multiple HTTP Ivy repositories"() {
        server.start()
        given:
        def repo = ivyRepo()
        def moduleA = repo.module('group', 'projectA').publish()
        def moduleB = repo.module('group', 'projectB').publish()
        def moduleC = repo.module('group', 'projectC').publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo1" }
    ivy { url "http://localhost:${server.port}/repo2" }
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
        server.expectGet('/repo1/group/projectA/1.0/ivy-1.0.xml', moduleA.ivyFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', moduleA.jarFile)

        // Handles missing in repo1
        server.expectGetMissing('/repo1/group/projectB/1.0/ivy-1.0.xml')
        // TODO - this shouldn't happen - resolver is trying to generate metadata based on presence of jar
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')

        server.expectGet('/repo2/group/projectB/1.0/ivy-1.0.xml', moduleB.ivyFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', moduleB.jarFile)

        // Handles from broken url in repo1 (but does not cache)
        server.addBroken('/repo1/group/projectC')
        server.expectGet('/repo2/group/projectC/1.0/ivy-1.0.xml', moduleC.ivyFile)
        server.expectGet('/repo2/group/projectC/1.0/projectC-1.0.jar', moduleC.jarFile)

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        server.addBroken('/repo1/group/projectC') // Will always re-attempt a broken repository
        // No extra calls for cached dependencies

        then:
        succeeds('listJars')
    }

    public void "can resolve dependencies from password protected HTTP Ivy repository"() {
        server.start()
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}/repo"

        credentials {
            password 'password'
            username 'username'
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        when:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', module.jarFile)

        then:
        succeeds('listJars')
    }

    public void "uses configured proxy to access remote HTTP repository"() {
        server.start()
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://external:8888/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        when:
        executer.withArguments("-Dhttp.proxyHost=localhost", "-Dhttp.proxyPort=${server.port}")

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('listJars')
    }

    public void "reports and recovers from missing module"() {
        server.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()
        
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
if (project.hasProperty('doNotCacheChangingModules')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
task showMissing << { println configurations.missing.files }
"""

        when:
        server.expectGetMissing('/group/projectA/1.2/ivy-1.2.xml')
        server.expectGetMissing('/group/projectA/1.2/projectA-1.2.jar')
        fails("showMissing")

        then:
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':missing\'.')
        failure.assertThatCause(Matchers.containsString('Could not find group:group, module:projectA, version:1.2.'))

        when:
        server.resetExpectations() // Missing status is cached
        fails('showMissing')

        then:
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':missing\'.')
        failure.assertThatCause(Matchers.containsString('Could not find group:group, module:projectA, version:1.2.'))
        
        when:
        server.resetExpectations()
        server.expectGet('/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/group/projectA/1.2/projectA-1.2.jar', module.jarFile)
        
        then:
        executer.withArguments("-PdoNotCacheChangingModules")
        succeeds('showMissing')
    }

    public void "reports and recovers from broken module"() {
        server.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.3')
        module.publish()

        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}
configurations { broken }
dependencies {
    broken 'group:projectA:1.3'
}
task showBroken << { println configurations.broken.files }
"""

        when:
        server.addBroken('/')
        fails("showBroken")

        then:
        failure.assertHasDescription('Execution failed for task \':showBroken\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':broken\'.')
        failure.assertHasCause('Could not resolve group:group, module:projectA, version:1.3')
        failure.assertHasCause("Could not GET 'http://localhost:${server.port}/group/projectA/1.3/ivy-1.3.xml'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        server.expectGet('/group/projectA/1.3/ivy-1.3.xml', module.ivyFile)
        server.expectGet('/group/projectA/1.3/projectA-1.3.jar', module.jarFile)
        
        then:
        succeeds("showBroken")
    }

    public void "reports and caches missing artifacts until changing module cache expiry"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}
configurations { compile }
if (project.hasProperty('doNotCacheChangingModules')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
dependencies {
    compile 'group:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        def module = ivyRepo().module('group', 'projectA', '1.2')
        module.publish()

        when:
        server.expectGet('/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGetMissing('/group/projectA/1.2/projectA-1.2.jar')

        then:
        fails "retrieve"
        failure.assertThatCause(containsString("Artifact 'group:projectA:1.2@jar' not found"))

        when:
        server.resetExpectations()

        then:
        fails "retrieve"
        failure.assertThatCause(containsString("Artifact 'group:projectA:1.2@jar' not found"))

        when:
        server.resetExpectations()
        server.expectGet('/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        executer.withArguments("-PdoNotCacheChangingModules")
        succeeds 'retrieve'
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    public void "reports and recovers from failed artifact download"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        def module = ivyRepo().module('group', 'projectA', '1.2')
        module.publish()

        when:
        server.expectGet('/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.addBroken('/group/projectA/1.2/projectA-1.2.jar')

        then:
        fails "retrieve"
        failure.assertThatCause(containsString("Download failed for artifact 'group:projectA:1.2@jar': Could not GET"))

        when:
        server.resetExpectations()
        server.expectGet('/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds "retrieve"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    public void "uses all configured patterns to resolve artifacts and caches result"() {
        server.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

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
        def repo = ivyRepo()
        def module = repo.module('org.name.here', 'projectA', '1.2')
        module.publish()

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

    IvyRepository ivyRepo() {
        return new IvyRepository(file('ivy-repo'))
    }
}
