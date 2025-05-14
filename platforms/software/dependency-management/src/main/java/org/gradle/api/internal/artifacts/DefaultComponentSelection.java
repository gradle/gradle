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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.MetadataProvider;
import org.jspecify.annotations.Nullable;

public class DefaultComponentSelection implements ComponentSelectionInternal {
    private final ModuleComponentIdentifier candidate;
    private final MetadataProvider metadataProvider;
    private boolean rejected;
    private String rejectionReason;

    public DefaultComponentSelection(ModuleComponentIdentifier candidate, MetadataProvider metadataProvider) {
        this.candidate = candidate;
        this.metadataProvider = metadataProvider;
    }

    @Override
    public ModuleComponentIdentifier getCandidate() {
        return candidate;
    }

    @Override
    public ComponentMetadata getMetadata() {
        if (metadataProvider.isUsable()) {
            return metadataProvider.getComponentMetadata();
        } else {
            return null;
        }

    }

    @Nullable
    @Override
    public <T> T getDescriptor(Class<T> descriptorClass) {
        if (metadataProvider.isUsable()) {
            if (IvyModuleDescriptor.class.isAssignableFrom(descriptorClass)) {
                return descriptorClass.cast(metadataProvider.getIvyModuleDescriptor());
            }
        }
        return null;
    }

    @Override
    public void reject(String reason) {
        rejected = true;
        rejectionReason = reason;
    }

    @Override
    public boolean isRejected() {
        return rejected;
    }

    @Override
    public String getRejectionReason() {
        return rejectionReason;
    }
}
