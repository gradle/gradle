/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider.views

import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.util.TestUtil

class SetPropertySetViewTest extends AbstractHasMultiValuesPropertyCollectionViewTest {

    SetProperty<String> setProperty

    def setup() {
        setProperty = TestUtil.propertyFactory().setProperty(String)
    }

    @Override
    protected <T extends HasMultipleValues<String> & Provider<Collection<String>>> T multiValueProperty() {
        return setProperty
    }

    @Override
    protected <T extends Collection<String>> T cast(Collection<String> collection) {
        return collection as Set<String>
    }

    @Override
    protected <T extends Collection<String>> T newCollection(HasMultipleValues<String> multipleValueProperty) {
        return new SetPropertySetView<>(multipleValueProperty as SetProperty<String>)
    }
}
