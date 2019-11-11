/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.isolated

import spock.lang.Specification

class IsolationSchemeTest extends Specification {
    def scheme = new IsolationScheme(SomeAction, SomeParams, Nothing)

    def "can extract parameters type"() {
        expect:
        scheme.parameterTypeFor(DirectUsage) == CustomParams
        scheme.parameterTypeFor(IndirectUsage) == CustomParams
        scheme.parameterTypeFor(NoParams) == null
        scheme.parameterTypeFor(ParameterizedType) == CustomParams
    }

    def "fails when base parameters type is used"() {
        when:
        scheme.parameterTypeFor(BaseParams)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Could not create the parameters for BaseParams: must use a sub-type of SomeParams as the parameters type. Use Nothing as the parameters type for implementations that do not take parameters."
    }

    def "fails when parameters type has not been declared"() {
        when:
        scheme.parameterTypeFor(RawActionType)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Could not create the parameters for RawActionType: must use a sub-type of SomeParams as the parameters type. Use Nothing as the parameters type for implementations that do not take parameters."
    }
}

interface SomeParams {
}

interface Nothing extends SomeParams {}

interface SomeAction<P extends SomeParams> {
}

interface CustomParams extends SomeParams {
}

class DirectUsage implements SomeAction<CustomParams> {
}

interface Indirect<P> extends SomeAction<CustomParams> {
}

class IndirectUsage implements Indirect<Number> {
}

class NoParams implements SomeAction<Nothing> {
}

class BaseParams implements SomeAction<SomeParams> {
}

class RawActionType implements SomeAction {
}

class ParameterizedType<T extends CustomParams> implements SomeAction<T> {
}
