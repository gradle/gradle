/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.tooling.internal.consumer.parameters

import spock.lang.Specification

class ConsumerOperationParametersTest extends Specification {
    
    def params = ConsumerOperationParameters.builder()
    
    def "null or empty arguments have the same meaning"() {
        when:
        params.arguments = null

        then:
        params.build().arguments == null

        when:
        params.arguments = []

        then:
        params.build().arguments == null

        when:
        params.arguments = ['-Dfoo']

        then:
        params.build().arguments == ['-Dfoo']
    }

    def "null or empty jvm arguments have the same meaning"() {
        when:
        params.jvmArguments = null

        then:
        params.build().jvmArguments == null

        when:
        params.jvmArguments = []

        then:
        params.build().jvmArguments == null

        when:
        params.jvmArguments = ['-Xmx']

        then:
        params.build().jvmArguments == ['-Xmx']
    }
}
