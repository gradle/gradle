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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.internal.component.local.model.LocalComponentMetaData;

import java.util.Set;

public class CompositeResolveLocalComponentFactory implements LocalComponentFactory {
    private final Set<LocalComponentFactory> factories;

    public CompositeResolveLocalComponentFactory(LocalComponentFactory... factories) {
        this.factories = Sets.newHashSet(factories);
    }

    public void addFactory(LocalComponentFactory factory) {
        factories.add(factory);
    }

    @Override
    public boolean canConvert(Object source) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public LocalComponentMetaData convert(Object context) {
        for (LocalComponentFactory factory : factories) {
            if (factory.canConvert(context)) {
                return factory.convert(context);
            }
        }
        throw new IllegalArgumentException("Unable to find a local converter factory for type "+context.getClass());
    }
}
