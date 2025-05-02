/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.base.internal.registry;

import com.google.common.reflect.TypeToken;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultDomainObjectSet;

public class DefaultLanguageTransformContainer extends DefaultDomainObjectSet<LanguageTransform<?, ?>> implements LanguageTransformContainer {
    public DefaultLanguageTransformContainer(CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        super(getLanguageTransformType(), collectionCallbackActionDecorator);
    }

    private static Class<LanguageTransform<?, ?>> getLanguageTransformType() {
        @SuppressWarnings("unchecked")
        Class<LanguageTransform<?, ?>> rawType = (Class<LanguageTransform<?, ?>>) new TypeToken<LanguageTransform<?, ?>>() {}.getRawType();
        return rawType;
    }
}
