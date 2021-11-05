/*
 * Copyright 2021 the original author or authors.
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

abstract class MutationFailureFromWithTypeConfigureEachDomainObjectContainerIntegrationTest extends AbstractDomainObjectContainerIntegrationTest {
    def "cannot execute mutation method #mutationMethod.key from withType.configureEach"() {
        buildFile << """
            testContainer.withType(testContainer.type).configureEach {
                ${mutationMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(mutationMethod.key))

        where:
        mutationMethod << getMutationMethods()
    }
}

class MutationFailureFromWithTypeConfigureEachNamedDomainObjectContainerIntegrationTest extends MutationFailureFromWithTypeConfigureEachDomainObjectContainerIntegrationTest implements AbstractNamedDomainObjectContainerIntegrationTest {
}

class MutationFailureFromWithTypeConfigureEachTaskContainerIntegrationTest extends MutationFailureFromWithTypeConfigureEachDomainObjectContainerIntegrationTest implements AbstractTaskContainerIntegrationTest {
}
