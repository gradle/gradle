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
package org.gradle.internal.extensibility;

import org.gradle.api.plugins.ExtensionsSchema;
import org.gradle.internal.Cast;

import java.util.Iterator;

public class DefaultExtensionsSchema implements ExtensionsSchema {

    public static ExtensionsSchema create(Iterable<? extends ExtensionSchema> schemas) {
        return new DefaultExtensionsSchema(Cast.<Iterable<ExtensionSchema>>uncheckedCast(schemas));
    }

    private final Iterable<ExtensionSchema> extensionSchemas;

    private DefaultExtensionsSchema(Iterable<ExtensionSchema> extensionSchemas) {
        this.extensionSchemas = extensionSchemas;
    }

    @Override
    public Iterator<ExtensionSchema> iterator() {
        return extensionSchemas.iterator();
    }

    @Override
    public Iterable<ExtensionSchema> getElements() {
        return this;
    }
}
