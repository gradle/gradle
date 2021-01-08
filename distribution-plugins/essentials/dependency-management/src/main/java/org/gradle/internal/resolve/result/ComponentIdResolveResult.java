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
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.RejectedVersion;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * The result of resolving a module version selector to a particular component id.
 * The result may optionally include the meta-data for the selected component, if it is cheaply available (for example, it was used to select the component).
 */
public interface ComponentIdResolveResult extends ResolveResult {
    /**
     * Returns the resolve failure, if any.
     */
    @Override
    @Nullable
    ModuleVersionResolveException getFailure();

    /**
     * Returns the identifier of the component.
     *
     * @throws org.gradle.internal.resolve.ModuleVersionResolveException If resolution was unsuccessful and the id is unknown.
     */
    ComponentIdentifier getId();

    /**
     * Returns the module version id of the component.
     *
     * @throws org.gradle.internal.resolve.ModuleVersionResolveException If resolution was unsuccessful and the id is unknown.
     */
    ModuleVersionIdentifier getModuleVersionId();

    /**
     * Returns the meta-data for the component, if it was available at resolve time.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the descriptor is not available.
     */
    @Nullable
    ComponentResolveMetadata getMetadata();

    /**
     * Returns true if the component id was resolved, but it was rejected by constraint.
     */
    boolean isRejected();

    /**
     * @return the list of unmatched versions, that is to say versions which were listed but didn't match the selector
     */
    Collection<String> getUnmatchedVersions();

    /**
     * @return the list of versions which were considered for this module but rejected.
     */
    Collection<RejectedVersion> getRejectedVersions();

    /**
     * Tags this resolve result, for visiting. This is a performance optimization. It will return
     * true if the last tagged object is different, false otherwise. This is meant to replace the
     * use of a hash set to collect the visited items.
     */
    boolean mark(Object o);

}
