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

package org.gradle.integtests.fixtures.problems

import groovy.transform.CompileStatic
import spock.lang.Issue

@CompileStatic
class ReceivedProblem {
    private final long operationId
    private final Map<String, Object> problemDetails

    ReceivedProblem(long operationId, Map<String, Object> problemDetails) {
        this.operationId = operationId
        this.problemDetails = problemDetails
    }

    /**
     * Proxies all calls, except for the `operationId` property, to the `problemDetails` map.
     * <p>
     * This is done in order to keep compatibility with our already existing tests.
     *
     * @param propertyName The name of the property to get.
     * @return The value of the property.
     */
    @Override
    @Issue("https://github.com/gradle/gradle/issues/27411")
    Object getProperty(String propertyName) {
        if (propertyName == 'operationId') {
            return operationId
        } else {
            return problemDetails[propertyName]
        }
    }

}
