/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.publish.Publication;

import java.util.LinkedHashMap;
import java.util.Map;

public class CompositePublicationFactory {
    private final Map<Class<? extends Publication>, PublicationFactory> factories = new LinkedHashMap<Class<? extends Publication>, PublicationFactory>();

    public void register(Class<? extends Publication> type, PublicationFactory factory) {
        factories.put(type, factory);
    }

    public <T extends Publication> T create(Class<T> type, String name) {
        for (Map.Entry<Class<? extends Publication>, PublicationFactory> entry : factories.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                return (T) entry.getValue().create(name);
            }
        }
        throw new InvalidUserDataException("Cannot create publications of type: " + type);
    }
}
