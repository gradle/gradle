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

import org.gradle.api.Nullable;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.Collection;
import java.util.Set;

/**
 * The result of attempting to resolve the list of versions for a particular module.
 */
public interface BuildableModuleVersionListingResolveResult extends ResourceAwareResolveResult, ResolveResult {

    static enum State {
        Listed, Failed, Unknown
    }

    /**
     * Returns the current state of this result.
     */
    State getState();

    /**
     * Returns the versions that match the selector.
     *
     * @throws ModuleVersionResolveException If the resolution was not successful.
     */
    Set<String> getVersions() throws ModuleVersionResolveException;

    @Nullable
    ModuleVersionResolveException getFailure();

    /**
     * Marks the module as having been listed to have the specified versions available.
     */
    void listed(Collection<String> versions);

    /**
     * Marks the list as failed with the given exception.
     */
    void failed(ModuleVersionResolveException failure);

    /**
     * Returns true if the result is from an authoritative source. Defaults to true.
     */
    boolean isAuthoritative();

    void setAuthoritative(boolean authoritative);
}
