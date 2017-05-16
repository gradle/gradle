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

package org.gradle.api.reporting.components.internal;

import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;

import java.util.List;

public class DiagnosticsServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            TypeAwareBinaryRenderer createBinaryRenderer(List<AbstractBinaryRenderer> renderers, ModelSchemaStore schemaStore) {
                TypeAwareBinaryRenderer renderer = new TypeAwareBinaryRenderer();
                renderer.register(new BinaryRenderer(schemaStore));
                for (AbstractBinaryRenderer binaryRenderer : renderers) {
                    renderer.register(binaryRenderer);
                }
                return renderer;
            }
        });
    }
}
