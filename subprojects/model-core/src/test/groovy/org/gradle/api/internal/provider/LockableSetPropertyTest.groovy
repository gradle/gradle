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

package org.gradle.api.internal.provider

import com.google.common.collect.ImmutableSet
import org.gradle.api.provider.SetProperty

class LockableSetPropertyTest extends LockableCollectionPropertySpec<Set<String>> {
    @Override
    CollectionPropertyInternal<String, Set<String>> target() {
        return Mock(TestProperty)
    }

    @Override
    LockableCollectionProperty<String, Set<String>> property(PropertyInternal<Set<String>> target) {
        return new LockableSetProperty<String>(target)
    }

    @Override
    Set<String> toMutable(Set<String> values) {
        return new LinkedHashSet<String>(values)
    }

    @Override
    Set<String> toImmutable(Set<String> values) {
        return ImmutableSet.copyOf(values)
    }

    @Override
    Set<String> someValue() {
        return ["abc"] as Set
    }

    @Override
    Set<String> someOtherValue() {
        return ["cde"] as Set
    }

    @Override
    Set<String> brokenValue() {
        return ["broken"] as Set
    }

    interface TestProperty extends CollectionPropertyInternal<String, Set<String>>, SetProperty<String> {}
}
