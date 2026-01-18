/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;

import java.util.List;

public class ComponentSelectionReasons {
    public static final ComponentSelectionDescriptorInternal REQUESTED = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.REQUESTED);
    public static final ComponentSelectionDescriptorInternal ROOT = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.ROOT);
    public static final ComponentSelectionDescriptorInternal FORCED = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.FORCED);
    public static final ComponentSelectionDescriptorInternal CONFLICT_RESOLUTION = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONFLICT_RESOLUTION);
    public static final ComponentSelectionDescriptorInternal SELECTED_BY_RULE = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.SELECTED_BY_RULE);
    public static final ComponentSelectionDescriptorInternal COMPOSITE_BUILD = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.COMPOSITE_BUILD);
    public static final ComponentSelectionDescriptorInternal CONSTRAINT = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONSTRAINT);
    public static final ComponentSelectionDescriptorInternal REJECTION = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.REJECTION);
    public static final ComponentSelectionDescriptorInternal BY_ANCESTOR = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.BY_ANCESTOR);

    public static ComponentSelectionReasonInternal requested() {
        return of(REQUESTED);
    }

    public static ComponentSelectionReasonInternal root() {
        return of(ROOT);
    }

    public static ComponentSelectionReasonInternal of(ComponentSelectionDescriptorInternal descriptions) {
        return new DefaultComponentSelectionReason(ImmutableList.of(descriptions));
    }

    public static ComponentSelectionReasonInternal of(ImmutableSet<ComponentSelectionDescriptorInternal> dependencyReasons) {
        assert !dependencyReasons.isEmpty();
        return new DefaultComponentSelectionReason(dependencyReasons.asList());
    }

    public static boolean isCauseExpected(ComponentSelectionDescriptor descriptor) {
        return descriptor.getCause() == ComponentSelectionCause.REQUESTED || descriptor.getCause() == ComponentSelectionCause.ROOT;
    }

    static class DefaultComponentSelectionReason implements ComponentSelectionReasonInternal {

        private final ImmutableList<ComponentSelectionDescriptorInternal> descriptions;

        // Package private since static factories enforce non-empty and no duplicates,
        // but the serializer can skip the set creation.
        DefaultComponentSelectionReason(ImmutableList<ComponentSelectionDescriptorInternal> descriptions) {
            this.descriptions = descriptions;
        }

        @Override
        public boolean isForced() {
            return hasCause(ComponentSelectionCause.FORCED);
        }

        private boolean hasCause(ComponentSelectionCause cause) {
            for (ComponentSelectionDescriptor description : descriptions) {
                if (description.getCause() == cause) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isConflictResolution() {
            return hasCause(ComponentSelectionCause.CONFLICT_RESOLUTION);
        }

        @Override
        public boolean isSelectedByRule() {
            return hasCause(ComponentSelectionCause.SELECTED_BY_RULE);
        }

        @Override
        public boolean isExpected() {
            return descriptions.size() == 1 && isCauseExpected(getLast());
        }

        @Override
        public boolean isCompositeSubstitution() {
            return hasCause(ComponentSelectionCause.COMPOSITE_BUILD);
        }

        @Override
        public String toString() {
            return getLast().toString();
        }

        @Override
        public List<ComponentSelectionDescriptorInternal> getDescriptions() {
            return descriptions;
        }

        @Override
        public boolean isConstrained() {
            return hasCause(ComponentSelectionCause.CONSTRAINT);
        }

        @Override
        public boolean hasCustomDescriptions() {
            for (ComponentSelectionDescriptorInternal description : descriptions) {
                if (description.hasCustomDescription()) {
                    return true;
                }
            }
            return false;
        }

        private ComponentSelectionDescriptorInternal getLast() {
            return descriptions.get(descriptions.size() - 1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DefaultComponentSelectionReason that = (DefaultComponentSelectionReason) o;
            return descriptions.equals(that.descriptions);
        }

        @Override
        public int hashCode() {
            return descriptions.hashCode();
        }

    }
}
