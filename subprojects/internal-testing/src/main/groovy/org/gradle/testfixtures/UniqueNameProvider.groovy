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

package org.gradle.testfixtures

import org.spockframework.runtime.SpockAssertionError
import org.spockframework.runtime.extension.builtin.UnrollNameProvider
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.NameProvider

class UniqueNameProvider implements NameProvider<IterationInfo> {

    private final Set<String> testMethodNames = new HashSet<>()
    private final NameProvider<IterationInfo> delegate

    UniqueNameProvider(FeatureInfo feature, String namePattern) {
        delegate = new UnrollNameProvider(feature, namePattern)
    }

    @Override
    String getName(IterationInfo iterationInfo) {
        def name = delegate.getName(iterationInfo)
        if (!testMethodNames.add(name)) {
            throw new SpockAssertionError("Test method name '$name' is not unique")
        }

        name
    }
}
