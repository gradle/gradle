/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.internal.metaobject.BeanDynamicObject;

import javax.annotation.Nullable;
import java.util.function.Function;

class HasPropertyMissingDynamicObject extends BeanDynamicObject {
    private final Function<String, Boolean> hasPropertyMissing;

    public HasPropertyMissingDynamicObject(Object bean, @Nullable Class<?> publicType, Function<String, Boolean> hasPropertyMissing) {
        super(bean, publicType);
        this.hasPropertyMissing = hasPropertyMissing;
    }

    @Override
    public boolean hasProperty(String name) {
        if (super.hasProperty(name)) {
            return true;
        } else {
            return hasPropertyMissing.apply(name);
        }
    }
}
