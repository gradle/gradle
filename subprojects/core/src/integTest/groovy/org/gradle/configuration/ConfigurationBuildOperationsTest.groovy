/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.configuration

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ConfigurationBuildOperationsTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
        rootProject.name = 'test'
        ${soutBuildOperationsListener()}
        """.stripIndent()
    }

    def "project configuration build operation events are emitted"() {
        when:
        run 'help', '-q'

        then:
        output.count('STARTED  Configure project :') == 1
        output.count('FINISHED Configure project :') == 1
    }

    def "project configuration failure build operations events are emitted"() {
        given:
        buildFile << '''
            error
        '''.stripIndent()

        when:
        fails 'help', '-q'

        then:
        output.count('STARTED  Configure project :') == 1
        output.count('FINISHED Configure project : A problem occurred configuring root project') == 1
    }

    def "plugin application build operation events are emitted"() {
        given:
        buildFile << '''
        apply plugin: 'base'
        '''.stripIndent()

        when:
        run 'help', '-q'

        then:
        output.count('STARTED  Apply plugin org.gradle.base') == 1
        output.count('FINISHED Apply plugin org.gradle.base') == 1
        output.count('STARTED  Apply plugin org.gradle.language.base.plugins.LifecycleBasePlugin') == 1
        output.count('FINISHED Apply plugin org.gradle.language.base.plugins.LifecycleBasePlugin') == 1
    }

    private static String soutBuildOperationsListener() {
        return '''
        import org.gradle.internal.progress.BuildOperationInternal
        import org.gradle.internal.progress.InternalBuildListener
        import org.gradle.internal.progress.OperationResult
        import org.gradle.internal.progress.OperationStartEvent

        class SoutBuildOperationsListener implements InternalBuildListener {
            @Override
            void started(BuildOperationInternal buildOperation, OperationStartEvent startEvent) {
                println "STARTED  $buildOperation.displayName"
            }

            @Override
            void finished(BuildOperationInternal buildOperation, OperationResult finishEvent) {
                println "FINISHED $buildOperation.displayName ${finishEvent.failure ? finishEvent.failure.message : ''}"
            }
        }

        gradle.addListener(new SoutBuildOperationsListener())
        '''.stripIndent()
    }
}
