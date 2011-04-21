/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import spock.lang.Specification
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1
import org.gradle.tooling.internal.protocol.ProjectVersion3
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1
import org.gradle.tooling.internal.protocol.BuildParametersVersion1

class ProgressLoggingConnectionTest extends Specification {
    final ConnectionVersion4 target = Mock()
    final BuildOperationParametersVersion1 params = Mock()
    final ProgressListenerVersion1 listener = Mock()
    final ProgressLoggingConnection connection = new ProgressLoggingConnection(target)

    def notifiesProgressListenerOfStartAndEndOfFetchingModel() {
        when:
        connection.getModel(ProjectVersion3, params)

        then:
        1 * listener.onOperationStart('Loading projects')
        1 * target.getModel(ProjectVersion3, params)
        1 * listener.onOperationEnd()
        _ * params.progressListener >> listener
        0 * _._
    }

    def notifiesProgressListenerOfStartAndEndOfExecutingBuild() {
        BuildParametersVersion1 buildParams = Mock()

        when:
        connection.executeBuild(buildParams, params)

        then:
        1 * listener.onOperationStart('Running build')
        1 * target.executeBuild(buildParams, params)
        1 * listener.onOperationEnd()
        _ * params.progressListener >> listener
        0 * _._
    }
}
