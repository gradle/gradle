/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api

class MutationFailureFromDisallowChangesDomainObjectContainerIntegrationTest extends AbstractDomainObjectContainerIntegrationTest {

    String disallowChangesMutationMessage(String methodName) {
        return "Cannot call ${methodName} on String collection as changes to this collection are disallowed."
    }

    Map<String, String> getStructuralMutationMethods() {
        [
            "add(T)":             "testContainer.add('mutate')",
            "addLater(Provider)": "testContainer.addLater(project.provider { 'mutate' })",
            "remove(Object)":     "testContainer.remove('existing')",
            "clear()":            "testContainer.clear()",
        ]
    }

    String makeContainer() {
        return "project.objects.domainObjectSet(String)"
    }

    @Override
    String getContainerStringRepresentation() {
        return "SomeType container"
    }

    @Override
    String registerExistingLazyElements() {
        return """
            ext.toBeRealized = testContainer.addLater(project.provider { 'toBeRealized' })
            ext.unrealized = testContainer.addLater(project.provider { 'unrealized' })
            ext.realized = testContainer.add('realized')
        """
    }

    def "cannot execute mutation method #mutationMethod.key after disallowChanges"() {
        buildFile << """
            testContainer.disallowChanges()
            ${mutationMethod.value}
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowChangesMutationMessage(mutationMethod.key))

        where:
        mutationMethod << getStructuralMutationMethods()
    }

    def "disallowChanges cannot be called from configureEach"() {
        buildFile << """
            testContainer.configureEach {
                testContainer.disallowChanges()
            }
        """

        expect:
        fails "help"
        failure.assertHasCause("DefaultDomainObjectSet#disallowChanges() on String collection cannot be executed in the current context.")
    }
}
