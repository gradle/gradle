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

package org.gradle.internal.progress

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildOperationServiceIntegrationTest extends AbstractIntegrationSpec {

    def "build can listen to build operations events"() {
        given:
        buildFile << '''
            import org.gradle.internal.progress.* 
            gradle.services.get(BuildOperationService).addListener(new InternalBuildListener() {
                @Override
                void started(BuildOperationInternal operation, OperationStartEvent startEvent) {
                    logger.lifecycle "START $operation.displayName"
                }
    
                @Override
                void finished(BuildOperationInternal operation, OperationResult finishEvent) {
                    logger.lifecycle "FINISH $operation.displayName"
                }
            })
        '''.stripIndent()

        when:
        succeeds 'help'

        then:
        result.output.contains 'START Task :help'
        result.output.contains 'FINISH Task :help'
    }
}
