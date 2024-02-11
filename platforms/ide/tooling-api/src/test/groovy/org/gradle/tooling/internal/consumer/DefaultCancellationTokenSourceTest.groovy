/*
 * Copyright 2014 the original author or authors.
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

class DefaultCancellationTokenSourceTest extends Specification {
    def source = new DefaultCancellationTokenSource()

    def 'token operation idempotent'() {
        when:
        def token1 = source.token()
        def token2 = source.token()

        then:
        token1 == token2
    }

    def 'can unpack token'() {
        expect:
        source.token().token != null
    }

    def 'can cancel'() {
        expect:
        !source.token().cancellationRequested

        when:
        source.cancel()

        then:
        source.token().cancellationRequested
    }
}
