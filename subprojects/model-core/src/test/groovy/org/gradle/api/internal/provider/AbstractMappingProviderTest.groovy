/*
 * Copyright 2020 the original author or authors.
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

class AbstractMappingProviderTest extends ProviderSpec<String> {
    @Override
    Provider<String> providerWithValue(String value) {
        return new TestProvider(Providers.of(value.replace("{", "").replace("}", "")))
    }

    @Override
    Provider<String> providerWithNoValue() {
        return new TestProvider(Providers.notDefined())
    }

    @Override
    Class<String> type() {
        return String
    }

    @Override
    String someValue() {
        "{s1}"
    }

    @Override
    String someOtherValue() {
        "{other1}"
    }

    @Override
    String someOtherValue2() {
        "{other2}"
    }

    @Override
    String someOtherValue3() {
        "{other3}"
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.ProviderManagedFactory()
    }

    class TestProvider extends AbstractMappingProvider<String, String> {
        TestProvider(ProviderInternal<? extends String> provider) {
            super(String, provider)
        }

        @Override
        protected String getMapDescription() {
            return "thing"
        }

        @Override
        protected String mapValue(String v) {
            return "{$v}"
        }
    }
}
