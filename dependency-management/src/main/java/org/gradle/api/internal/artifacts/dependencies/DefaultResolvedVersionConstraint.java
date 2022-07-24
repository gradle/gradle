/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.UnionVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;

import java.util.List;

public class DefaultResolvedVersionConstraint implements ResolvedVersionConstraint {
    private final VersionSelector preferredVersionSelector;
    private final VersionSelector requiredVersionSelector;
    private final VersionSelector rejectedVersionsSelector;
    private final boolean isStrict;
    private final boolean rejectAll;
    private final boolean isDynamic;

    public DefaultResolvedVersionConstraint(VersionConstraint parent, VersionSelectorScheme scheme) {
        this(parent.getRequiredVersion(), parent.getPreferredVersion(), parent.getStrictVersion(), parent.getRejectedVersions(), scheme);
    }

    @VisibleForTesting
    public DefaultResolvedVersionConstraint(String requiredVersion, String preferredVersion, String strictVersion, List<String> rejectedVersions, VersionSelectorScheme scheme) {
        // For now, required and preferred are treated the same

        isStrict = !strictVersion.isEmpty();
        String version = isStrict ? strictVersion : requiredVersion;
        this.requiredVersionSelector = scheme.parseSelector(version);
        this.preferredVersionSelector = preferredVersion.isEmpty() ? null : scheme.parseSelector(preferredVersion);

        if (isStrict) {
            VersionSelector rejectionForStrict = getRejectionForStrict(version, scheme);
            if (!rejectedVersions.isEmpty()) {
                VersionSelector explicitRejected = toRejectSelector(scheme, rejectedVersions);
                this.rejectedVersionsSelector = new UnionVersionSelector(ImmutableList.of(rejectionForStrict, explicitRejected));
            } else {
                this.rejectedVersionsSelector = rejectionForStrict;
            }
            rejectAll = false;
        } else {
            this.rejectedVersionsSelector = toRejectSelector(scheme, rejectedVersions);
            rejectAll = isRejectAll(version, rejectedVersions);
        }
        this.isDynamic = doComputeIsDynamic();
    }

    private static VersionSelector getRejectionForStrict(String version, VersionSelectorScheme versionSelectorScheme) {
        VersionSelector preferredSelector = versionSelectorScheme.parseSelector(version);
        return versionSelectorScheme.complementForRejection(preferredSelector);
    }

    private static VersionSelector toRejectSelector(VersionSelectorScheme scheme, List<String> rejectedVersions) {
        if (rejectedVersions.size()>1) {
            return UnionVersionSelector.of(rejectedVersions, scheme);
        }
        return rejectedVersions.isEmpty() ? null : scheme.parseSelector(rejectedVersions.get(0));
    }

    @Override
    public VersionSelector getPreferredSelector() {
        return preferredVersionSelector;
    }

    @Override
    public VersionSelector getRequiredSelector() {
        return requiredVersionSelector;
    }

    @Override
    public VersionSelector getRejectedSelector() {
        return rejectedVersionsSelector;
    }

    @Override
    public boolean isRejectAll() {
        return rejectAll;
    }

    @Override
    public boolean isDynamic() {
        return isDynamic;
    }

    @Override
    public boolean isStrict() {
        return isStrict;
    }

    @Override
    public boolean accepts(String candidate) {
        boolean accepted = true;
        if (requiredVersionSelector != null) {
            accepted = requiredVersionSelector.accept(candidate);
        } else if (preferredVersionSelector != null) {
            accepted = preferredVersionSelector.accept(candidate);
        }
        if (accepted && rejectedVersionsSelector != null) {
            accepted = !rejectedVersionsSelector.accept(candidate);
        }
        return accepted;
    }

    @Override
    public boolean canBeStable() {
        return canBeStable(preferredVersionSelector)
            && canBeStable(requiredVersionSelector)
            && canBeStable(rejectedVersionsSelector);
    }

    private static boolean canBeStable(VersionSelector vs) {
        if (vs == null) {
            return true;
        }
        return vs.canShortCircuitWhenVersionAlreadyPreselected();
    }

    private boolean doComputeIsDynamic() {
        if (requiredVersionSelector != null) {
            return requiredVersionSelector.isDynamic();
        }
        if (preferredVersionSelector != null) {
            return preferredVersionSelector.isDynamic();
        }
        return false;
    }

    private static boolean isRejectAll(String preferredVersion, List<String> rejectedVersions) {
        return "".equals(preferredVersion)
            && hasMatchAllSelector(rejectedVersions);
    }

    private static boolean hasMatchAllSelector(List<String> rejectedVersions) {
        for (String version : rejectedVersions) {
            if ("+".equals(version)) {
                return true;
            }
        }
        return false;
    }
}
