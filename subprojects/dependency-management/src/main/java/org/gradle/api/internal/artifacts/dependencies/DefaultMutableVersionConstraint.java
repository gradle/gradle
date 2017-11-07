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

import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.VersionConstraintInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;

import java.util.Collections;
import java.util.List;

import static com.google.common.base.Strings.nullToEmpty;

public class DefaultMutableVersionConstraint extends AbstractVersionConstraint implements VersionConstraintInternal {
    private String prefer;
    private List<String> rejects;

    public DefaultMutableVersionConstraint(String version, boolean strict) {
        this.prefer = version;
        if (strict) {
            doStrict();
        } else {
            this.rejects = Collections.emptyList();
        }
    }

    public DefaultMutableVersionConstraint(String version, List<String> rejects) {
        this.prefer = nullToEmpty(version);
        this.rejects = rejects;
    }

    private void doStrict() {
        // When strict version is used, we need to parse the preferred selector early, in order to compute its complement.
        // Hopefully this shouldn't happen too often. If it happens to become a performance problem, we need to reconsider
        // how we compute the "reject" clause
        DefaultVersionSelectorScheme versionSelectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator());
        VersionSelector preferredSelector = versionSelectorScheme.parseSelector(prefer);
        VersionSelector rejectedSelector = versionSelectorScheme.complementForRejection(preferredSelector);
        this.rejects = Collections.singletonList(rejectedSelector.getSelector());
    }

    public DefaultMutableVersionConstraint(String version) {
        this(version, false);
    }

    @Override
    public ImmutableVersionConstraint asImmutable() {
        String v = prefer == null ? "" : prefer;
        return new DefaultImmutableVersionConstraint(v, rejects);
    }

    @Override
    public String getPreferredVersion() {
        return prefer;
    }

    @Override
    public void prefer(String version) {
        this.prefer = version;
        this.rejects = Collections.emptyList();
    }

    @Override
    public void strictly(String version) {
        this.prefer = version;
        doStrict();
    }

    @Override
    public List<String> getRejectedVersions() {
       return rejects;
    }
}
