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


import org.gradle.api.provider.Provider
import org.gradle.internal.state.ManagedFactory

class ProvidersTest extends ProviderSpec<Integer> {
    @Override
    Provider providerWithNoValue() {
        return Providers.notDefined()
    }

    @Override
    Provider<Integer> providerWithValue(Integer value) {
        return Providers.of(value)
    }

    @Override
    Class<Integer> type() {
        return Integer
    }

    @Override
    Integer someValue() {
        return 12
    }

    @Override
    Integer someOtherValue() {
        return 123
    }

    @Override
    Integer someOtherValue2() {
        return 1234
    }

    @Override
    Integer someOtherValue3() {
        return 12345
    }

    @Override
    boolean isNoValueProviderImmutable() {
        return true
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.ProviderManagedFactory()
    }
}
