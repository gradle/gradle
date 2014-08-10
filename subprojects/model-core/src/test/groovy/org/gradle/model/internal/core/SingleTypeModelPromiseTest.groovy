/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core

import spock.lang.Specification

class SingleTypeModelPromiseTest extends Specification {

    def "promise"() {
        when:
        def type = new ModelType<List<CharSequence>>() {}
        def promise = new SingleTypeModelPromise(type)

        then:
        promise.asReadOnly(type)
        promise.asWritable(type)

        when:
        def supertype = new ModelType<List<? extends Object>>() {}

        then:
        promise.asReadOnly(supertype)
        promise.asWritable(supertype)

        when:
        def unrelated = ModelType.of(Integer)

        then:
        !promise.asReadOnly(unrelated)
        !promise.asWritable(unrelated)
    }
}
