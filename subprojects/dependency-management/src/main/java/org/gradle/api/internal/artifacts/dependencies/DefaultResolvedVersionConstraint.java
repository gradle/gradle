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
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.UnionVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;

import java.util.List;

public class DefaultResolvedVersionConstraint implements ResolvedVersionConstraint {
    private final VersionSelector preferredVersionSelector;
    private final VersionSelector rejectedVersionsSelector;
    private final boolean rejectAll;

    public DefaultResolvedVersionConstraint(VersionSelector preferredVersionSelector, VersionSelector rejectedVersionsSelector) {
        this.preferredVersionSelector = preferredVersionSelector;
        this.rejectedVersionsSelector = rejectedVersionsSelector;
        rejectAll = false;
    }

    public DefaultResolvedVersionConstraint(VersionConstraint parent, VersionSelectorScheme scheme) {
        this(parent.getPreferredVersion(), parent.getStrictVersion(), parent.getRejectedVersions(), scheme);
    }

    @VisibleForTesting
    public DefaultResolvedVersionConstraint(String preferredVersion, String strictVersion, List<String> rejected, VersionSelectorScheme scheme) {
        boolean strict = !strictVersion.isEmpty();
        String version = strict ? strictVersion : preferredVersion;
        this.preferredVersionSelector = scheme.parseSelector(version);

        List<String> rejectedVersions = rejected;
        if (strict) {
            rejectedVersions = Lists.newArrayList(rejectedVersions);
            rejectedVersions.add(getRejectionForStrict(version, scheme));
        }
        this.rejectedVersionsSelector = toRejectSelector(scheme, rejectedVersions);
        rejectAll = isRejectAll(version, rejectedVersions);
    }

    private String getRejectionForStrict(String version, VersionSelectorScheme versionSelectorScheme) {
        VersionSelector preferredSelector = versionSelectorScheme.parseSelector(version);
        return versionSelectorScheme.complementForRejection(preferredSelector).getSelector();
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
    public VersionSelector getRejectedSelector() {
        return rejectedVersionsSelector;
    }

    @Override
    public boolean isRejectAll() {
        return rejectAll;
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
