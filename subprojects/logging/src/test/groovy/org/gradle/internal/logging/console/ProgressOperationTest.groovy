/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.logging.console

import org.gradle.internal.logging.events.OperationIdentifier
import spock.lang.Specification

class ProgressOperationTest extends Specification {

    def "message prefers status"() {
        given:
        ProgressOperation progressOperation = new ProgressOperation("SHORT_DESCRIPTION", "STATUS", "CATEGORY", new OperationIdentifier(1), null)

        expect:
        progressOperation.getMessage() == "STATUS"
    }

    def "message falls back to short description"() {
        given:
        ProgressOperation progressOperation = new ProgressOperation("SHORT_DESCRIPTION", null, "CATEGORY", new OperationIdentifier(1), null)

        expect:
        progressOperation.getMessage() == "SHORT_DESCRIPTION"
    }

    def "message is null if all other inputs are null"() {
        given:
        ProgressOperation progressOperation = new ProgressOperation(null, null, "CATEGORY", new OperationIdentifier(1), null)

        expect:
        progressOperation.getMessage() == null
    }
}
