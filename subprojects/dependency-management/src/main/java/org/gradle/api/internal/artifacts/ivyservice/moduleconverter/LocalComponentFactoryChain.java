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

import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.internal.component.local.model.LocalComponentMetaData;

import java.util.List;

public class LocalComponentFactoryChain implements LocalComponentFactory {
    private final List<LocalComponentFactory> factories;

    public LocalComponentFactoryChain(List<LocalComponentFactory> factories) {
        this.factories = factories;
    }

    @Override
    public boolean canConvert(Object source) {
        for (LocalComponentFactory factory : factories) {
            if (factory.canConvert(source)) {
                return true;
            }
        }
        return false;
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
