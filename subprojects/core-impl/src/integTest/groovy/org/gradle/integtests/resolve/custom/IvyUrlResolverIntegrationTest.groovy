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
package org.gradle.integtests.resolve.custom

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.junit.Rule

class IvyUrlResolverIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Rule ProgressLoggingFixture progressLogging = new ProgressLoggingFixture(executer, temporaryFolder)

    def setup() {
        server.expectUserAgent(null) // custom resolver uses apache/ivy as user agent strings
    }

    public void "can resolve and cache dependencies from an HTTP Ivy repository"() {
        given:
        final IvyHttpRepository repo = ivyHttpRepo
        def projectA = repo.module('group', 'projectA', '1.2').publish()
        repo.module('group', 'projectB').publish()
        def projectB11 = repo.module('group', 'projectB', '1.1').publish()
        def projectB20 = repo.module('group', 'projectB', '2.0').publish()

        and:
        buildFile << """
repositories {
    add(new org.apache.ivy.plugins.resolver.URLResolver()) {
        name = "repo"
        addIvyPattern("${repo.ivyPattern}")
        addArtifactPattern("${repo.artifactPattern}")
    }
}
configurations {
    simple
    dynamic
    dynamic2
    dynamicMissing
}
dependencies {
    simple 'group:projectA:1.2'
    dynamic 'group:projectB:1.+'
    dynamic2 'group:projectB:2.+'
    dynamicMissing 'group:projectC:1.+'
}
task retrieveSimple(type: Sync) {
  from configurations.simple
  into 'simple'
}
task retrieveDynamic(type: Sync) {
  from configurations.dynamic
  into 'dynamic'
}
task retrieveDynamic2(type: Sync) {
  from configurations.dynamic2
  into 'dynamic2'
}
task retrieveDynamicMissing(type: Sync) {
  from configurations.dynamicMissing
  into 'dynamicMissing'
}
"""
        when:
        projectA.ivy.expectHead()
        projectA.ivy.expectGet()
        projectA.jar.expectHead()
        projectA.jar.expectGet()
        executer.withDeprecationChecksDisabled()

        and:
        succeeds 'retrieveSimple'

        then:
        file('simple').assertHasDescendants('projectA-1.2.jar')

        and:
        progressLogging.downloadProgressLogged(projectA.ivy.uri)
        progressLogging.downloadProgressLogged(projectA.jar.uri)

        when:
        server.resetExpectations()

        // No extra calls for cached dependencies
        and:
        executer.withDeprecationChecksDisabled()
        succeeds 'retrieveSimple'

        then:
        file('simple').assertHasDescendants('projectA-1.2.jar')

        when:
        repo.expectDirectoryListGet('group', 'projectB')
        projectB11.ivy.expectHead()
        projectB11.ivy.expectGet()
        projectB11.jar.expectHead()
        projectB11.jar.expectGet()

        projectB11.ivy.expectGet()

        and:
        executer.withDeprecationChecksDisabled()
        succeeds 'retrieveDynamic'

        then:
        file('dynamic').assertHasDescendants('projectB-1.1.jar')

        and:
        progressLogging.downloadProgressLogged(projectB11.ivy.uri)
        progressLogging.downloadProgressLogged(projectB11.jar.uri)

        when:
        server.resetExpectations()

        // No extra calls for cached dependencies
        and:
        executer.withDeprecationChecksDisabled()
        succeeds 'retrieveDynamic'

        then:
        file('dynamic').assertHasDescendants('projectB-1.1.jar')

        when:
        repo.expectDirectoryListGet('group', 'projectB')
        projectB20.ivy.expectHead()
        projectB20.ivy.expectGet()
        projectB20.jar.expectHead()
        projectB20.jar.expectGet()
        executer.withDeprecationChecksDisabled()

        projectB20.ivy.expectGet()

        and:
        succeeds 'retrieveDynamic2'

        then:
        file('dynamic2').assertHasDescendants('projectB-2.0.jar')
    }

    public void "reports module missing when directory listing for module is empty or broken"() {
        given:
        def repo = ivyHttpRepo

        and:
        buildFile << """
repositories {
    add(new org.apache.ivy.plugins.resolver.URLResolver()) {
        name = "repo"
        addIvyPattern("${repo.ivyPattern}")
        addArtifactPattern("${repo.artifactPattern}")
    }
}
configurations { compile }
dependencies { compile 'org.gradle:projectA:1.+' }
task testResolve(type: Sync) {
  println configurations.compile.files
}
"""
        when:
        server.resetExpectations()
        2.times {repo.expectDirectoryListGetBroken("org.gradle", "projectA") }
        executer.withDeprecationChecksDisabled()

        then:
        fails 'testResolve'

        and:
        failure.assertHasCause "Could not find any version that matches org.gradle:projectA:1.+"

        when:
        2.times { repo.expectDirectoryListGetMissing("org.gradle", "projectA") }
        executer.withDeprecationChecksDisabled()

        then:
        fails 'testResolve'

        and:
        failure.assertHasCause "Could not find any version that matches org.gradle:projectA:1.+"
    }

    public void "honours changing patterns from custom resolver"() {
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2-SNAPSHOT').publish()

        and:
        buildFile << """
repositories {
    add(new org.apache.ivy.plugins.resolver.URLResolver()) {
        name = "repo"
        addIvyPattern("${ivyHttpRepo.ivyPattern}")
        addArtifactPattern("${ivyHttpRepo.artifactPattern}")
        changingMatcher = 'regexp'
        changingPattern = '.*SNAPSHOT.*'
    }
}
configurations { compile }
configurations.compile.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
dependencies { compile 'group:projectA:1.2-SNAPSHOT' }
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        when:
        module.ivy.expectHead()
        module.ivy.expectGet()
        module.jar.expectHead()
        module.jar.expectGet()
        executer.withDeprecationChecksDisabled()

        run 'retrieve'

        then:
        def jarFile = file('libs/projectA-1.2-SNAPSHOT.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when:
        module.publishWithChangedContent()

        server.resetExpectations()
        // Server will be hit to get updated versions
        module.ivy.expectHead()
        module.ivy.expectGet()
        module.jar.expectHead()
        module.jar.expectGet()
        executer.withDeprecationChecksDisabled()

        run 'retrieve'

        then:
        def changedJarFile = file('libs/projectA-1.2-SNAPSHOT.jar')
        changedJarFile.assertHasChangedSince(snapshot)
        changedJarFile.assertIsCopyOf(module.jarFile)
    }
}
