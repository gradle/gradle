/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.internal.Describables;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class CachingComponentSelectionDescriptorFactory implements ComponentSelectionDescriptorFactory {

    private final InMemoryLoadingCache<Key, ComponentSelectionDescriptorInternal> descriptors;

    public CachingComponentSelectionDescriptorFactory(InMemoryCacheFactory cacheFactory) {
        this.descriptors = cacheFactory.create(k ->
            k.description != null
                ? new DefaultComponentSelectionDescriptor(k.cause, Describables.of(k.description))
                : new DefaultComponentSelectionDescriptor(k.cause)
        );
    }

    @Override
    public ComponentSelectionDescriptorInternal newDescriptor(ComponentSelectionCause cause, String reason) {
        return descriptors.get(new Key(cause, reason));
    }

    @Override
    public ComponentSelectionDescriptorInternal newDescriptor(ComponentSelectionCause cause) {
        return descriptors.get(new Key(cause, null));
    }

    private static class Key {

        private final ComponentSelectionCause cause;
        private final @Nullable String description;

        private Key(ComponentSelectionCause cause, @Nullable String description) {
            this.cause = cause;
            this.description = description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            if (cause != key.cause) {
                return false;
            }
            return Objects.equals(description, key.description);
        }

        @Override
        public int hashCode() {
            int result = cause.hashCode();
            result = 31 * result + Objects.hashCode(description);
            return result;
        }

    }

}
