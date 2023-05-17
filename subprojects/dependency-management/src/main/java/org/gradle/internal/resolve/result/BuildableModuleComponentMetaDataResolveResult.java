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

import org.gradle.internal.resolve.ModuleVersionResolveException;

import javax.annotation.Nullable;

/**
 * The result of attempting to resolve a component id to the meta-data for the component.
 *
 * @param <T> the resolution result type
 */
public interface BuildableModuleComponentMetaDataResolveResult<T> extends ResourceAwareResolveResult, ErroringResolveResult<ModuleVersionResolveException> {
    enum State {
        Resolved, Missing, Failed, Unknown, Redirect
    }

    /**
     * Returns the current state of this descriptor.
     */
    State getState();

    /**
     * Returns the meta-data.
     *
     * @throws ModuleVersionResolveException If the resolution was not successful.
     */
    T getMetaData() throws ModuleVersionResolveException;

    @Override
    @Nullable
    ModuleVersionResolveException getFailure();

    /**
     * Marks the module version as resolved, with the given meta-data.
     */
    void resolved(T metaData);

    /**
     * Replaces the meta-data for this result. This result must already be resolved.
     */
    void setMetadata(T metaData);

    /**
     * Marks the resolve as failed with the given exception.
     */
    @Override
    void failed(ModuleVersionResolveException failure);

    /**
     * Marks the module version as definitely missing.
     */
    void missing();

    /**
     * Returns true if the result is from an authoritative source. Defaults to true.
     */
    boolean isAuthoritative();

    void setAuthoritative(boolean authoritative);

    /**
     * Marks this result as being redirected to Gradle metadata, which
     * is a temporary performance only optimization
     */
    void redirectToGradleMetadata();

    boolean shouldUseGradleMetatada();
}
