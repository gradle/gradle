package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class MavenBrokenRemoteResolveIntegrationTest extends AbstractDependencyResolutionTest {
    public void "reports POM that cannot be parsed"() {
        server.start()
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task showBroken << { println configurations.compile.files }
"""

        and:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()
        module.pomFile.text = "<project>"

        when:
        module.pom.expectGet()

        then:
        fails "showBroken"
        failure.assertResolutionFailure(":compile")
    }

}
