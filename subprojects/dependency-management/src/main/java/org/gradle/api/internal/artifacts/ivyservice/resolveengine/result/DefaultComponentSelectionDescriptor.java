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
import org.gradle.api.artifacts.result.ComponentSelectionCause;

public class DefaultComponentSelectionDescriptor implements ComponentSelectionDescriptorInternal {
    private final ComponentSelectionCause cause;
    private final String description;

    public DefaultComponentSelectionDescriptor(ComponentSelectionCause cause) {
        this(cause, cause.getDefaultReason());
    }

    public DefaultComponentSelectionDescriptor(ComponentSelectionCause cause, String description) {
        this.cause = cause;
        this.description = description;
    }

    @Override
    public ComponentSelectionCause getCause() {
        return cause;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean hasCustomDescription() {
        return !cause.getDefaultReason().equals(description);
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
        return cause == that.cause
            && Objects.equal(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(cause, description);
    }

    @Override
    public String toString() {
        return description;
    }

    @Override
    public ComponentSelectionDescriptorInternal withReason(String reason) {
        if (description.equals(reason)) {
            return this;
        }
        return new DefaultComponentSelectionDescriptor(cause, reason);
    }
}
