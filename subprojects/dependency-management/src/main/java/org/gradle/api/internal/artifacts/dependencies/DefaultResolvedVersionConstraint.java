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

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;

import java.util.List;

public class DefaultResolvedVersionConstraint extends DefaultImmutableVersionConstraint implements ResolvedVersionConstraint {
    private final VersionSelector preferredVersionSelector;
    private final VersionSelector rejectedVersionsSelector;

    public DefaultResolvedVersionConstraint(VersionConstraint parent, VersionSelectorScheme scheme) {
        super(parent.getPreferredVersion(), parent.getRejectedVersions());

        String preferredVersion = parent.getPreferredVersion();
        List<String> rejectedVersions = parent.getRejectedVersions();
        this.preferredVersionSelector = scheme.parseSelector(preferredVersion);
        this.rejectedVersionsSelector = toRejectSelector(scheme, rejectedVersions);
    }

    private static VersionSelector toRejectSelector(VersionSelectorScheme scheme, List<String> rejectedVersions) {
        if (rejectedVersions.size()>1) {
            throw new UnsupportedOperationException("Multiple rejects are not yet supported");
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
}
