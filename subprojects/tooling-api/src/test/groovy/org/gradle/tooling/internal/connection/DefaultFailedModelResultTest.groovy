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

package org.gradle.tooling.internal.connection

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.BuildIdentifier
import spock.lang.Specification

class DefaultFailedModelResultTest extends Specification {

    def "throws failure when model is requested"() {
        given:
        def cause = new GradleConnectionException("Something's wrong")
        def result = new DefaultFailedModelResult(Stub(BuildIdentifier), cause)
        when:
        result.getModel()
        then:
        def failure = thrown(GradleConnectionException)
        failure == cause
    }
}
