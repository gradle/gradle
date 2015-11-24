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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import org.gradle.api.internal.DefaultDomainObjectSet;

import java.util.Collections;
import java.util.List;

public class DefaultLanguageRegistry extends DefaultDomainObjectSet<LanguageRegistration<?>> implements LanguageRegistry {
    public DefaultLanguageRegistry() {
        super(getLanguageRegistrationType());
    }

    private static Class<LanguageRegistration<?>> getLanguageRegistrationType() {
        @SuppressWarnings("unchecked")
        Class<LanguageRegistration<?>> rawType = (Class<LanguageRegistration<?>>) new TypeToken<LanguageRegistration<?>>() {
        }.getRawType();
        return rawType;
    }

    @Override
    public String getSupportedTypeNames() {
        List<String> names = Lists.newArrayList();
        for (LanguageRegistration<?> languageRegistration : this) {
            names.add(languageRegistration.getSourceSetType().getDisplayName());
        }
        Collections.sort(names);
        return names.isEmpty() ? "(None)" : Joiner.on(", ").join(names);
    }
}
