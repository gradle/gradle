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
package org.gradle.internal.component.model;

import org.gradle.internal.service.ServiceRegistry;
import org.gradle.platform.base.BinarySpec;

import java.util.List;

public class BinarySpecToArtifactConverterRegistry {
    private final ServiceRegistry serviceRegistry;

    public BinarySpecToArtifactConverterRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends BinarySpec> BinarySpecToArtifactConverter<T> getConverter(T binarySpec) {
        // we need to use the service registry here because it is not reliable to call "configure" on a service
        // which is not defined in the same service provider class as the service where it is consumed
        List<BinarySpecToArtifactConverter> converters = serviceRegistry.getAll(BinarySpecToArtifactConverter.class);
        for (BinarySpecToArtifactConverter converter : converters) {
            if (converter.getType().isAssignableFrom(binarySpec.getClass())) {
                return (BinarySpecToArtifactConverter<T>) converter;
            }
        }
        return null;
    }
}
