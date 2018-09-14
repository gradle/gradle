/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.NamedDomainObjectCollection

abstract class AbstractNamedDomainObjectCollectionSpec<T> extends AbstractDomainObjectCollectionSpec<T> {
    abstract NamedDomainObjectCollection<T> getContainer()

    @Override
    protected List<MethodUnderTest> getQueryMethodsUnderTest() {
        def result = []
        result.addAll(super.getQueryMethodsUnderTest())
        result.addAll([
            methodUnderTest("getByName(String)") { container.getByName("a") },
        ])
        return result
    }

    @Override
    List<ConfigurationUnderTest> getLazyConfigurationsUnderTest() {
        List<ConfigurationUnderTest> result = []
        result.addAll(super.getLazyConfigurationsUnderTest())
        result.addAll([
            configurationUnderTest("existing NamedDomainObjectProvider.configure") { container.add(a); container.named("a").configure(it) }
        ])
        return result
    }
}
