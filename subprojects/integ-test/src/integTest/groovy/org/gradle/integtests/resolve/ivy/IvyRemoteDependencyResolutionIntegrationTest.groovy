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
import org.junit.Rule
import org.hamcrest.Matchers

class IvyRemoteDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()

    public void "can resolve and cache dependencies from an HTTP Ivy repository"() {
        distribution.requireOwnUserHomeDir()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)
        server.start()

        and:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}/repo"
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

        expect:
        succeeds('listJars')

//        given:
        server.resetExpectations()
        // No extra calls for cached dependencies

//        expect:
        succeeds('listJars')
    }

    public void "can resolve and cache dependencies from multiple HTTP Ivy repositories"() {
        distribution.requireOwnUserHomeDir()

        given:
        def repo = ivyRepo()
        def module1 = repo.module('group', 'projectA', '1.2')
        module1.publish()
        def module2 = repo.module('group', 'projectB', '1.3')
        module2.publish()

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module1.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module1.jarFile)
        server.expectGetMissing('/repo/group/projectB/1.3/ivy-1.3.xml')
        server.expectGet('/repo2/group/projectB/1.3/ivy-1.3.xml', module2.ivyFile)
        server.expectGet('/repo2/group/projectB/1.3/projectB-1.3.jar', module2.jarFile)

        // TODO - this shouldn't happen - it's already found B's ivy.xml on repo2
        server.expectGetMissing('/repo/group/projectB/1.3/projectB-1.3.jar')

        server.start()

        and:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}/repo"
    }
    ivy {
        url "http://localhost:${server.port}/repo2"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2', 'group:projectB:1.3'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar', 'projectB-1.3.jar']
}
"""

        expect:
        succeeds('listJars')


//        given:
        server.resetExpectations()
        // No extra calls for cached dependencies

//        expect:
        succeeds('listJars')
    }

    public void "can resolve dependencies from password protected HTTP Ivy repository"() {
        distribution.requireOwnUserHomeDir()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', module.jarFile)
        server.start()

        and:
        buildFile << """
repositories {
    ivy {
        userName 'username'
        password 'password'
        url "http://localhost:${server.port}/repo"
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

        expect:
        succeeds('listJars')
    }

    public void "reports missing and failed HTTP downloads"() {
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
task show << { println configurations.compile.files }
"""

        when:
        server.expectGetMissing('/group/projectA/1.2/ivy-1.2.xml')
        server.expectGetMissing('/group/projectA/1.2/projectA-1.2.jar')
        fails("show")

        then:
        failure.assertHasDescription('Execution failed for task \':show\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertThatCause(Matchers.containsString('Module group:projectA:1.2 not found.'))

        when:
        server.addBroken('/')
        fails("show")

        then:
        failure.assertHasDescription('Execution failed for task \':show\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertThatCause(Matchers.containsString('Could not resolve group#projectA;1.2'))
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

        server.expectGetMissing('/first/group/projectA/1.2/ivy-1.2.xml')
        server.expectGetMissing('/first/group/projectA/1.2/projectA-1.2.jar')
        server.expectGetMissing('/second/projectA/1.2/ivy.xml')
        server.expectGetMissing('/second/projectA/1.2/projectA-1.2.jar')
        server.expectGet('/third/projectA/1.2/ivy.xml', module.ivyFile)
        server.expectGet('/third/projectA/1.2/projectA-1.2.jar', module.jarFile)

        expect:
        succeeds('show')

//        given:
        server.resetExpectations()

//        expect:
        succeeds('show')
    }

    IvyRepository ivyRepo() {
        return new IvyRepository(file('ivy-repo'))
    }
}
