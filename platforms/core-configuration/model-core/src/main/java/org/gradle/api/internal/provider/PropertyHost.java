/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.internal.provider.provenance.MutationKind;
import org.gradle.api.internal.provider.provenance.MutationRecord;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.Nullable;

@ServiceScope({Scope.Global.class, Scope.Project.class})
public interface PropertyHost {
    PropertyHost NO_OP = producer -> null;

    /**
     * Returns null if the host allows reads of its state, or a string that explains why reads are not allowed.
     */
    @Nullable
    String beforeRead(@Nullable ModelObject producer);

    /**
     * Does this host attribute property mutations to the user code that performs them?
     * <p>
     * Asked once, when a property is created, so that a property whose host does not track provenance never
     * calls back into the host while being mutated.
     */
    default boolean tracksMutationProvenance() {
        return false;
    }

    /**
     * Returns provenance for a mutation of the given kind happening right now, or null when the host does not
     * track provenance. Hosts that do track it answer from the user code application that is currently running.
     * <p>
     * This is the one seam through which a property learns who is mutating it: the host is already handed to
     * every property when it is created. Only called when {@link #tracksMutationProvenance()} is true.
     */
    @Nullable
    default MutationRecord currentMutation(MutationKind kind) {
        return null;
    }
}
