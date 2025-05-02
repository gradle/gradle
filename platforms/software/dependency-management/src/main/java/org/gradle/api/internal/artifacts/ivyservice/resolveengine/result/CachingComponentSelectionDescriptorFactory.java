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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.internal.Describables;

import java.util.concurrent.ExecutionException;

public class CachingComponentSelectionDescriptorFactory implements ComponentSelectionDescriptorFactory {
    private final Cache<Key, ComponentSelectionDescriptor> descriptors = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .build();

    @Override
    public ComponentSelectionDescriptor newDescriptor(ComponentSelectionCause cause, String reason) {
        return getOrCreate(cause, reason);
    }

    @Override
    public ComponentSelectionDescriptor newDescriptor(ComponentSelectionCause cause) {
        try {
            return descriptors.get(new Key(cause, cause.getDefaultReason()), () -> new DefaultComponentSelectionDescriptor(cause));
        } catch (ExecutionException e) {
            return new DefaultComponentSelectionDescriptor(cause);
        }
    }

    private ComponentSelectionDescriptor getOrCreate(ComponentSelectionCause cause, String description) {
        try {
            return descriptors.get(new Key(cause, description), () -> newDescriptorInstance(cause, description));
        } catch (ExecutionException e) {
            return newDescriptorInstance(cause, description);
        }
    }

    private ComponentSelectionDescriptor newDescriptorInstance(ComponentSelectionCause cause, String description) {
        return new DefaultComponentSelectionDescriptor(cause, Describables.of(description));
    }

    private static class Key {
        private final ComponentSelectionCause cause;
        private final String description;

        private Key(ComponentSelectionCause cause, String description) {
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
            return description.equals(key.description);
        }

        @Override
        public int hashCode() {
            int result = cause.hashCode();
            result = 31 * result + description.hashCode();
            return result;
        }
    }
}
