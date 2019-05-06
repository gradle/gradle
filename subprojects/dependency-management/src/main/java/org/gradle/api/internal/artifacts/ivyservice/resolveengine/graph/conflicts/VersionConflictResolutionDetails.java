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

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class VersionConflictResolutionDetails implements Describable {
    private final Collection<? extends ComponentResolutionState> candidates;
    private final int hashCode;

    public VersionConflictResolutionDetails(Collection<? extends ComponentResolutionState> candidates) {
        this.candidates = candidates;
        this.hashCode = candidates.hashCode();
    }

    public Collection<? extends ComponentResolutionState> getCandidates() {
        return candidates;
    }

    @Override
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder(16 + 16 * candidates.size());
        sb.append("between versions ");
        Iterator<? extends ComponentResolutionState> it = candidates.iterator();
        boolean more = false;
        while (it.hasNext()) {
            ComponentResolutionState next = it.next();
            if (more) {
                if (it.hasNext()) {
                    sb.append(", ");
                } else {
                    sb.append(" and ");
                }
            }
            more = true;
            sb.append(next.getId().getVersion());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VersionConflictResolutionDetails that = (VersionConflictResolutionDetails) o;
        return Objects.equal(candidates, that.candidates);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * For a single module, conflict resolution can happen several times. However, we want to keep only one version
     * conflict resolution cause, listing all modules which participated in resolution. So this method is going to iterate
     * over all causes, and if it finds that version conflict resolution kicked in several times, will merge all candidates
     * in order to report it once with all candidates.
     *
     * This method tries its best not to create new lists if not required.
     *
     * @param descriptors all selection descriptors
     * @return a filtered descriptors list, with merged conflict version resolution
     */
    public static List<ComponentSelectionDescriptorInternal> mergeCauses(List<ComponentSelectionDescriptorInternal> descriptors) {
        List<VersionConflictResolutionDetails> byVersionConflictResolution = collectVersionConflictCandidates(descriptors);
        if (byVersionConflictResolution != null && byVersionConflictResolution.size()>1) {
            Set<ComponentResolutionState> allCandidates = mergeAllCandidates(byVersionConflictResolution);
            List<ComponentSelectionDescriptorInternal> merged = Lists.newArrayListWithCapacity(descriptors.size()-1);
            boolean added = false;
            for (ComponentSelectionDescriptorInternal descriptor : descriptors) {
                if (isByVersionConflict(descriptor)) {
                    if (!added) {
                        merged.add(ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(new VersionConflictResolutionDetails(allCandidates)));
                    }
                    added = true;
                } else {
                    merged.add(descriptor);
                }
            }
            return merged;
        }
        return descriptors;
    }

    private static Set<ComponentResolutionState> mergeAllCandidates(List<VersionConflictResolutionDetails> byVersionConflictResolution) {
        Set<ComponentResolutionState> allCandidates = Sets.newLinkedHashSet();
        for (VersionConflictResolutionDetails versionConflictResolutionDetails : byVersionConflictResolution) {
            allCandidates.addAll(versionConflictResolutionDetails.getCandidates());
        }
        return allCandidates;
    }

    private static List<VersionConflictResolutionDetails> collectVersionConflictCandidates(List<ComponentSelectionDescriptorInternal> descriptors) {
        List<VersionConflictResolutionDetails> byVersionConflictResolution = null;
        for (ComponentSelectionDescriptorInternal descriptor : descriptors) {
            if (isByVersionConflict(descriptor)) {
                if (byVersionConflictResolution == null) {
                    byVersionConflictResolution = Lists.newArrayListWithCapacity(descriptors.size());
                }
                byVersionConflictResolution.add((VersionConflictResolutionDetails) descriptor.getDescribable());
            }
        }
        return byVersionConflictResolution;
    }

    private static boolean isByVersionConflict(ComponentSelectionDescriptorInternal descriptor) {
        return descriptor.getCause() == ComponentSelectionCause.CONFLICT_RESOLUTION && descriptor.getDescribable() instanceof VersionConflictResolutionDetails;
    }

}
