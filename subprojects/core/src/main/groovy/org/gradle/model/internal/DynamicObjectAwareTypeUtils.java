/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal;

import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.model.internal.manage.schema.extract.ManagedInstanceTypeUtils;
import org.gradle.model.internal.type.ModelType;

public class DynamicObjectAwareTypeUtils {
    public static <T> ModelType<? super T> extractModelTypeFromType(ModelType<T> type) {
        if (DynamicObjectAware.class.isAssignableFrom(type.getRawClass())) {
            return ModelType.of(type.getRawClass().getSuperclass());
        }
        return type;
    }

    public static <T> ModelType<? super T> extractModelTypeFromInstance(T instance) {
        ModelType<? super T> managedType = ManagedInstanceTypeUtils.extractModelTypeFromInstance(instance);
        return extractModelTypeFromType(managedType);
    }
}
