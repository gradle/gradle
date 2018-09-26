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
import org.gradle.internal.Actions

abstract class AbstractNamedDomainObjectContainerSpec<T> extends AbstractNamedDomainObjectCollectionSpec<T> {
    abstract NamedDomainObjectCollection<T> getContainer()

    @Override
    protected Map<String, Closure> getMutatingMethods() {
        return super.getMutatingMethods() + [
            "create(String)": { container.create("b") },
            "create(String, Action)": { container.create("b", Actions.doNothing()) },
            "register(String)": { container.register("b") },
            "register(String, Action)": { container.register("b", Actions.doNothing()) },
        ]
    }
}
