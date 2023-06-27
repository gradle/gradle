package org.gradle.operations.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ProjectIsolationInitializationBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits settings finalized event when not configured"() {
        given:
        file("buildSrc/src/main/java/Thing.java") << "class Thing {}"

        when:
        succeeds("help")

        then:
        events().enabled == [false]
    }

    def "emits settings finalized event when explicitly #status"() {
        given:
        file("buildSrc/src/main/java/Thing.java") << "class Thing {}"

        when:
        succeeds("help", "-Dorg.gradle.unsafe.isolated-projects=$enabled")

        then:
        events().enabled == [enabled]

        when:
        succeeds("help", "-Dorg.gradle.unsafe.isolated-projects=$enabled")

        then:
        events().enabled == [enabled]

        where:
        status     | enabled
        "enabled"  | true
        "disabled" | false
    }

    List<ProjectIsolationSettingsFinalizedProgressDetails> events() {
        return operations.progress(ProjectIsolationSettingsFinalizedProgressDetails).details
    }

}
