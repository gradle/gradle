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

class ResolutionOverrideIntegrationTest extends AbstractHttpDependencyResolutionTest {
    public void "will refresh non-changing module when run with --refresh-dependencies"() {
        given:
        def module = mavenHttpRepo.module('org.name', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}
configurations { compile }
dependencies { compile 'org.name:projectA:1.2' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
        and:
        module.allowAll()

        when:
        succeeds 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')
        def snapshot = file('libs/projectA-1.2.jar').snapshot()

        when:
        module.publishWithChangedContent()

        and:
        server.resetExpectations()
        module.pom.expectHead()
        module.pom.sha1.expectGet()
        module.pom.expectGet()
        module.artifact.expectHead()
        module.artifact.sha1.expectGet()
        module.artifact.expectGet()

        and:
        executer.withArguments('--refresh-dependencies')
        succeeds 'retrieve'

        then:
        file('libs/projectA-1.2.jar').assertIsCopyOf(module.artifactFile).assertHasChangedSince(snapshot)
    }

    public void "will recover from missing module when run with --refresh-dependencies"() {
        given:
        def module = mavenHttpRepo.module('org.name', 'projectA', '1.2').publish()
        def artifact = module.artifact

        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
    }
}
configurations { missing }
dependencies {
    missing 'org.name:projectA:1.2'
}
task showMissing { doLast { println configurations.missing.files } }
"""

        when:
        module.pom.expectGetMissing()
        artifact.expectHeadMissing()

        then:
        fails("showMissing")

        when:
        server.resetExpectations()
        module.pom.expectGet()
        module.getArtifact().expectGet()

        then:
        executer.withArguments("--refresh-dependencies")
        succeeds('showMissing')
    }

    public void "will recover from missing artifact when run with --refresh-dependencies"() {
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'org.name:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        def module = mavenHttpRepo.module('org.name', 'projectA', '1.2').publish()
        def artifact = module.artifact

        when:
        module.pom.expectGet()
        artifact.expectGetMissing()

        then:
        fails "retrieve"

        when:
        server.resetExpectations()
        module.pom.expectHead()
        artifact.expectGet()

        then:
        executer.withArguments("--refresh-dependencies")
        succeeds 'retrieve'
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    public void "will not expire cache entries when run with offline flag"() {

        given:
        def module = mavenHttpRepo.module("org.name", "unique", "1.0-SNAPSHOT").publish()

        and:
        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}
configurations { compile }
configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}
dependencies {
    compile "org.name:unique:1.0-SNAPSHOT"
}
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when: "Server handles requests"
        module.allowAll()

        and: "We resolve dependencies"
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('unique-1.0-SNAPSHOT.jar')
        def snapshot = file('libs/unique-1.0-SNAPSHOT.jar').snapshot()

        when:
        module.publishWithChangedContent()

        and: "We resolve again, offline"
        server.resetExpectations()
        executer.withArguments('--offline')
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('unique-1.0-SNAPSHOT.jar')
        file('libs/unique-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshot)
    }

    public void "does not attempt to contact server when run with offline flag"() {
        given:
        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}
configurations { compile }
dependencies { compile 'org.name:projectA:1.2' }
task listJars {
    doLast {
        assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
    }
}
"""

        when:
        executer.withArguments("--offline")

        then:
        fails 'listJars'

        and:
        failure.assertHasDescription('Execution failed for task \':listJars\'.')
        failure.assertResolutionFailure(":compile")
            .assertHasCause('No cached version of org.name:projectA:1.2 available for offline mode')
    }
}
