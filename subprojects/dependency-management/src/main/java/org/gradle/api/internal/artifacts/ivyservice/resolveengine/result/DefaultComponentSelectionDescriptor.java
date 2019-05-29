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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.base.Objects;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.internal.Describables;

public class DefaultComponentSelectionDescriptor implements ComponentSelectionDescriptorInternal {
    private final ComponentSelectionCause cause;
    private final Describable description;
    private final boolean hasCustomDescription;
    private final int hashCode;
    private final boolean isEquivalentToForce;

    public DefaultComponentSelectionDescriptor(ComponentSelectionCause cause) {
        this(cause, Describables.of(cause.getDefaultReason()), false, cause == ComponentSelectionCause.FORCED);
    }

    public DefaultComponentSelectionDescriptor(ComponentSelectionCause cause, Describable description) {
        this(cause, description, true, cause == ComponentSelectionCause.FORCED);
    }

    private DefaultComponentSelectionDescriptor(ComponentSelectionCause cause, Describable description, boolean hasCustomDescription, boolean isEquivalentToForce) {
        this.cause = cause;
        this.description = description;
        this.hasCustomDescription = hasCustomDescription;
        this.isEquivalentToForce = isEquivalentToForce;
        if (hasCustomDescription) {
            this.hashCode = 31 * (31 * cause.hashCode() + description.hashCode()) + (isEquivalentToForce ? 1 : 0);
        } else {
            this.hashCode = 31 * cause.hashCode() + (isEquivalentToForce ? 1 : 0);
        }
    }

    @Override
    public ComponentSelectionCause getCause() {
        return cause;
    }

    @Override
    public String getDescription() {
        return description.getDisplayName();
    }

    @Override
    public boolean hasCustomDescription() {
        return hasCustomDescription;
    }

    @Override
    public Describable getDescribable() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultComponentSelectionDescriptor that = (DefaultComponentSelectionDescriptor) o;
        return hashCode == that.hashCode
            && cause == that.cause
            && isEquivalentToForce == that.isEquivalentToForce
            && Objects.equal(description, that.description);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return description.getDisplayName();
    }

    @Override
    public ComponentSelectionDescriptorInternal withDescription(Describable description) {
        if (this.description.equals(description)) {
            return this;
        }
        return new DefaultComponentSelectionDescriptor(cause, description, true, isEquivalentToForce);
    }

    @Override
    public ComponentSelectionDescriptorInternal markAsEquivalentToForce() {
        return new DefaultComponentSelectionDescriptor(cause, description, hasCustomDescription, true);
    }

    @Override
    public boolean isEquivalentToForce() {
        return isEquivalentToForce;
    }
}
