package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskDefinitionIntegrationSpec extends AbstractIntegrationSpec {

    def "unsupported task parameter fails with decent error message"() {

        buildFile << "task a(Type:Copy)"
        when:
        fails 'a'
        then:
        failure.assertHasCause("Could not create task 'a': Unknown argument(s) in task definition: [Type]")
    }
}
