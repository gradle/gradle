/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.exceptions

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE

class DefaultMultiCauseExceptionIntegrationTest  extends AbstractIntegrationSpec {
    def 'when tasks throw exceptions that offer resolutions, those resolutions are included'() {
        given:
        buildFile << """
            ${defineTestException()}

            tasks.register('myTask') {
                doLast {
                    throw new TestResolutionProviderException('resolution1')
                }
            }
        """

        when:
        fails 'myTask'

        then:
        failure.assertHasDescription("Execution failed for task ':myTask'.")
            .assertHasResolutions(
                'resolution1',
                STACKTRACE_MESSAGE,
                INFO_DEBUG,
                SCAN,
                GET_HELP)
    }

    def 'when tasks throw multi cause exceptions with resolutions offered by the causes, those resolutions are included'() {
        given:
        buildFile << """
            ${defineTestException()}

            tasks.register('myTask') {
                doLast {
                    def fail1 = new TestResolutionProviderException('resolution1')
                    def fail2 = new TestResolutionProviderException('resolution2')
                    throw new org.gradle.internal.exceptions.DefaultMultiCauseException('failure', fail1, fail2)
                }
            }
        """

        when:
        fails 'myTask'

        then:
        failure.assertHasDescription("Execution failed for task ':myTask'.")
            .assertHasCause('failure')
            .assertHasResolutions(
                'resolution1',
                'resolution2',
                STACKTRACE_MESSAGE,
                INFO_DEBUG,
                SCAN,
                GET_HELP)
    }

    def 'when tasks throw multi cause exceptions with the same resolution offered by multiple causes, those resolutions are not duplicated'() {
        given:
        buildFile << """
            ${defineTestException()}

            tasks.register('myTask') {
                doLast {
                    def fail1 = new TestResolutionProviderException('resolution1')
                    def fail2 = new TestResolutionProviderException('resolution1')
                    throw new org.gradle.internal.exceptions.DefaultMultiCauseException('failure', fail1, fail2)
                }
            }
        """

        when:
        fails 'myTask'

        then:
        failure.assertHasDescription("Execution failed for task ':myTask'.")
            .assertHasCause('failure')
            .assertHasResolutions(
                'resolution1',
                STACKTRACE_MESSAGE,
                INFO_DEBUG,
                SCAN,
                GET_HELP)
    }

    private String defineTestException() {
        return """
            class TestResolutionProviderException extends RuntimeException implements org.gradle.internal.exceptions.ResolutionProvider {
                private final String resolution

                TestResolutionProviderException(String resolution) {
                    this.resolution = resolution
                }

                @Override
                List<String> getResolutions() {
                    return Collections.singletonList(resolution)
                }
            }
        """
    }
}
