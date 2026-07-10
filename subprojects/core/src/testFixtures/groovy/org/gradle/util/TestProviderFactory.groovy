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

package org.gradle.util

import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider

public class TestProviderFactory extends DefaultProviderFactory {

    @Override
    Provider<String> environmentVariable(String variableName) {
        return Providers.ofNullable(System.getenv(variableName))
    }

    @Override
    Provider<String> environmentVariable(Provider<String> variableName) {
        return environmentVariable(variableName.get())
    }

    @Override
    Provider<String> systemProperty(String propertyName) {
        return Providers.ofNullable(System.getProperty(propertyName))
    }

    @Override
    Provider<String> systemProperty(Provider<String> propertyName) {
        return systemProperty(propertyName.get());
    }

    @Override
    Provider<String> gradleProperty(String propertyName) {
        return Providers.notDefined()
    }

    @Override
    Provider<String> gradleProperty(Provider<String> propertyName) {
        return Providers.notDefined()
    }

}
