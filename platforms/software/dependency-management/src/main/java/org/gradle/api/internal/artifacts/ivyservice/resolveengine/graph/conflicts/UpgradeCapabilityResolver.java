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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import com.google.common.collect.Sets;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.util.internal.VersionNumber;

import java.util.Collection;
import java.util.Set;

/**
 * A resolver which will automatically upgrade a capability to its latest version. It will evict the components
 * which are not associated with the selected version, but will not perform a selection by itself.
 */
public class UpgradeCapabilityResolver implements CapabilitiesConflictHandler.Resolver {
    @Override
    public void resolve(CapabilitiesConflictHandler.ResolutionDetails details) {
        Collection<? extends Capability> capabilityVersions = details.getCapabilityVersions();
        if (capabilityVersions.size() > 1) {
            Set<Capability> sorted = Sets.newTreeSet((o1, o2) -> {
                VersionNumber v1 = VersionNumber.parse(o1.getVersion());
                VersionNumber v2 = VersionNumber.parse(o2.getVersion());
                return v2.compareTo(v1);
            });
            sorted.addAll(capabilityVersions);
            boolean first = true;
            for (Capability capability : sorted) {
                DisplayName reason = Describables.of("latest version of capability", ((CapabilityInternal) capability).getCapabilityId());
                boolean isFirst = first;
                details.getCandidates(capability).forEach(cand -> {
                    cand.byReason(reason);
                    if (!isFirst) {
                        cand.evict();
                    }
                });
                first = false;
            }
        }
    }
}
