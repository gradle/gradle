/*
 * Copyright 2012 the original author or authors.
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


package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.junit.Rule

class EclipseClasspathResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule ProgressLoggingFixture progressLogging

    def "does not download source and javadoc artifacts from HTTP Maven repository until required"() {
        given:
        server.start()

        def projectA = mavenRepo().module('group', 'projectA', '1.0')
        projectA.artifact(classifier: 'sources')
        projectA.artifact(classifier: 'javadoc')
        projectA.publish()
        def sourceJar = projectA.artifactFile(classifier: 'sources')
        def javadocJar = projectA.artifactFile(classifier: 'javadoc')

        buildFile << """
apply plugin: 'java'
apply plugin: 'eclipse'
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
}
dependencies {
    compile 'group:projectA:1.0'
}
eclipse { classpath { downloadJavadoc = true } }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar']
}
"""

        when:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        then:
        succeeds 'listJars'

        when:
        server.resetExpectations()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0-sources.jar', sourceJar)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0-javadoc.jar', javadocJar)

        then:
        succeeds 'eclipseClasspath'
    }

    def "only attempts to download missing artifacts from HTTP Maven repository once"() {
        given:
        server.start()

        def projectA = mavenRepo().module('group', 'projectA', '1.0')
        projectA.publish()

        buildFile << """
apply plugin: 'java'
apply plugin: 'eclipse'
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
}
dependencies {
    compile 'group:projectA:1.0'
}
eclipse { classpath { downloadJavadoc = true } }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar']
}
"""

        when:
        server.resetExpectations()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0-sources.jar')
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0-javadoc.jar')

        then:
        succeeds 'eclipseClasspath'

        when:
        server.resetExpectations()

        then:
        succeeds 'eclipseClasspath'
    }
}