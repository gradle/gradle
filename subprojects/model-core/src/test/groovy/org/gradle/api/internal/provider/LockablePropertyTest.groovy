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

import org.gradle.api.provider.Property

class LockablePropertyTest extends LockablePropertySpec<String> {
    @Override
    String toMutable(String value) {
        return value
    }

    @Override
    String toImmutable(String value) {
        return value
    }

    @Override
    PropertyInternal<String> target() {
        return Mock(TestProperty)
    }

    @Override
    LockableProperty<String> property(PropertyInternal<String> target) {
        new LockableProperty<String>(target)
    }

    PropertyInternal<String> target = target()
    LockableProperty<String> property = property(target)

    @Override
    String someValue() {
        "abc"
    }

    @Override
    String someOtherValue() {
        "cde"
    }

    @Override
    String brokenValue() {
        "broken"
    }

    @Override
    String mutate(String value) {
        "more"
    }

    interface TestProperty extends Property<String>, PropertyInternal<String>, ProviderInternal<String> {}
}
