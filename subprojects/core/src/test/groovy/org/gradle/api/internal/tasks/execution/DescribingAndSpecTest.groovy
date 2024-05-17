/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.execution

import org.gradle.api.specs.Spec
import spock.lang.Specification

class DescribingAndSpecTest extends Specification {
    private final def dummyObject = new Object();

    void isSatisfiedWhenEmpty() {
        when:
        def spec = new DescribingAndSpec<Object>()

        then:
        spec == DescribingAndSpec.empty()
        spec.isEmpty()
        spec.findUnsatisfiedSpec(dummyObject) == null
        spec.isSatisfiedBy(dummyObject)
    }

    void isSatisfiedWithTrue() {
        when:
        def spec = new DescribingAndSpec<Object>({ true }, "must be true")

        then:
        !spec.isEmpty()
        spec.findUnsatisfiedSpec(dummyObject) == null
        spec.isSatisfiedBy(dummyObject)
    }

    void isSatisfiedWithFalse() {
        when:
        def spec = new DescribingAndSpec<Object>({ false }, "must be true")

        then:
        !spec.isEmpty()
        def foundSpec = spec.findUnsatisfiedSpec(dummyObject)
        foundSpec != null
        foundSpec.displayName == "must be true"
        !spec.isSatisfiedBy(dummyObject)
    }

    void isSatisfiedWhenAdded() {
        when:
        def spec = new DescribingAndSpec<Object>()
        spec = spec.and({ true }, "must be true")

        then:
        !spec.isEmpty()
        spec.findUnsatisfiedSpec(dummyObject) == null
        spec.isSatisfiedBy(dummyObject)

        when:
        Spec<Object> condition2 = { false }
        spec = spec.and(condition2, "condition2 must be true")
                   .and({ true }, "closure must be true")

        then:
        !spec.isEmpty()
        def foundSpec = spec.findUnsatisfiedSpec(dummyObject)
        foundSpec != null
        foundSpec.displayName == "condition2 must be true"
        !spec.isSatisfiedBy(dummyObject)
    }
}
