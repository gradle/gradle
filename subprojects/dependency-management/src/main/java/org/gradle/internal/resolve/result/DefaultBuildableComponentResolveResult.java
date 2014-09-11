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

package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public class DefaultBuildableComponentResolveResult extends DefaultResourceAwareResolveResult implements BuildableComponentResolveResult {
    private ComponentResolveMetaData metaData;
    private ModuleVersionResolveException failure;

    public DefaultBuildableComponentResolveResult failed(ModuleVersionResolveException failure) {
        metaData = null;
        this.failure = failure;
        return this;
    }

    public void notFound(ModuleVersionSelector versionSelector) {
        failed(new ModuleVersionNotFoundException(versionSelector, getAttempted()));
    }

    public void notFound(ModuleVersionIdentifier versionIdentifier) {
        failed(new ModuleVersionNotFoundException(versionIdentifier, getAttempted()));
    }

    public void resolved(ComponentResolveMetaData metaData) {
        this.metaData = metaData;
    }

    public void setMetaData(ComponentResolveMetaData metaData) {
        assertResolved();
        this.metaData = metaData;
    }

    public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
        assertResolved();
        return metaData.getId();
    }

    public ComponentResolveMetaData getMetaData() throws ModuleVersionResolveException {
        assertResolved();
        return metaData;
    }

    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    private void assertResolved() {
        assertHasResult();
        if (failure != null) {
            throw failure;
        }
    }

    private void assertHasResult() {
        if (!hasResult()) {
            throw new IllegalStateException("No result has been specified.");
        }
    }

    public boolean hasResult() {
        return failure != null || metaData != null;
    }

    public void applyTo(BuildableComponentIdResolveResult idResolve) {
        super.applyTo(idResolve);
        if (failure != null) {
            idResolve.failed(failure);
        }
        if (metaData != null) {
            idResolve.resolved(metaData);
        }
    }
}
