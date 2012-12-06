package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.junit.Rule

class SamplesClientModuleDependenciesIntegrationTest extends AbstractIntegrationSpec {
    @Rule Sample sample = new Sample("clientModuleDependencies")

    def "resolve shared"() {
        inDirectory(sample.dir.file("shared"))

        expect:
        // the actual testing is done in the build script
        succeeds("testDeps")
    }

    def "resolve api"() {
        inDirectory(sample.dir.file("api"))

        expect:
        // the actual testing is done in the build script
        succeeds("testDeps")
    }
}
