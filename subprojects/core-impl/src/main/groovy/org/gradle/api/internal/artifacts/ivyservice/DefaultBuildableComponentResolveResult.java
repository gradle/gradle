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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;

public class DefaultBuildableComponentResolveResult implements BuildableComponentResolveResult {
    private ComponentMetaData metaData;
    private ModuleVersionResolveException failure;

    public DefaultBuildableComponentResolveResult failed(ModuleVersionResolveException failure) {
        metaData = null;
        this.failure = failure;
        return this;
    }

    public void notFound(ModuleVersionSelector versionSelector) {
        failed(new ModuleVersionNotFoundException(versionSelector));
    }

    public void resolved(ComponentMetaData metaData) {
        this.metaData = metaData;
    }

    public void setMetaData(ComponentMetaData metaData) {
        assertResolved();
        this.metaData = metaData;
    }

    public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
        assertResolved();
        return metaData.getId();
    }

    public ComponentMetaData getMetaData() throws ModuleVersionResolveException {
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
        if (failure == null && metaData == null) {
            throw new IllegalStateException("No result has been specified.");
        }
    }
}
