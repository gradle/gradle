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

package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public class DefaultBuildableComponentIdResolveResult extends DefaultResourceAwareResolveResult implements BuildableComponentIdResolveResult {
    private ModuleVersionResolveException failure;
    private ComponentResolveMetaData metaData;
    private ComponentIdentifier id;
    private ModuleVersionIdentifier moduleVersionId;

    public boolean hasResult() {
        return id != null || failure != null;
    }

    public ModuleVersionResolveException getFailure() {
        return failure;
    }

    public ComponentIdentifier getId() {
        assertResolved();
        return id;
    }

    public ModuleVersionIdentifier getModuleVersionId() {
        assertResolved();
        return moduleVersionId;
    }

    public ComponentResolveMetaData getMetaData() {
        assertResolved();
        return metaData;
    }

    public void resolved(ComponentIdentifier id, ModuleVersionIdentifier moduleVersionIdentifier) {
        reset();
        this.id = id;
        this.moduleVersionId = moduleVersionIdentifier;
    }

    public void resolved(ComponentResolveMetaData metaData) {
        reset();
        this.metaData = metaData;
        id = metaData.getComponentId();
        moduleVersionId = metaData.getId();
    }

    public void failed(ModuleVersionResolveException failure) {
        reset();
        this.failure = failure;
    }

    private void assertResolved() {
        if (failure != null) {
            throw failure;
        }
        if (id == null) {
            throw new IllegalStateException("Not resolved.");
        }
    }

    private void reset() {
        failure = null;
        metaData = null;
        id = null;
        moduleVersionId = null;
    }
}
