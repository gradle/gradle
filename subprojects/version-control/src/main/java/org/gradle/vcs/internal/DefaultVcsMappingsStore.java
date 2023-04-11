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

package org.gradle.vcs.internal;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.Actions;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VersionControlSpec;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public class DefaultVcsMappingsStore implements VcsMappingsStore, VcsResolver {
    private final Set<Action<? super VcsMapping>> rootVcsMappings = Sets.newLinkedHashSet();
    private final Map<Gradle, Set<Action<? super VcsMapping>>> vcsMappings = Maps.newHashMap();
    private final VcsMappingFactory vcsMappingFactory;

    public DefaultVcsMappingsStore(VcsMappingFactory vcsMappingFactory) {
        this.vcsMappingFactory = vcsMappingFactory;
    }

    @Override
    public VcsResolver asResolver() {
        return this;
    }

    @Nullable
    @Override
    public VersionControlSpec locateVcsFor(ModuleComponentSelector selector) {
        if (!hasRules()) {
            return null;
        }
        VcsMappingInternal mapping = vcsMappingFactory.create(selector);
        applyTo(mapping);
        if (mapping.hasRepository()) {
            return mapping.getRepository();
        }
        return null;
    }

    private void applyTo(VcsMappingInternal mapping) {
        Actions.composite(rootVcsMappings).execute(mapping);
        if (!mapping.hasRepository()) {
            Set<VersionControlSpec> resolutions = Sets.newHashSet();
            for (Gradle gradle : vcsMappings.keySet()) {
                Actions.composite(vcsMappings.get(gradle)).execute(mapping);
                if (mapping.hasRepository()) {
                    resolutions.add(mapping.getRepository());
                }
            }
            if (resolutions.size() > 1) {
                Set<String> resolutionDisplayNames = CollectionUtils.collect(resolutions, Describable::getDisplayName);
                throw new GradleException("Conflicting external source dependency rules were found in nested builds for " + mapping.getRequested().getDisplayName() + ":\n  " + CollectionUtils.join("\n  ", resolutionDisplayNames));
            }
        }
    }

    @Override
    public boolean hasRules() {
        return !(vcsMappings.isEmpty() && rootVcsMappings.isEmpty());
    }

    @Override
    public void addRule(final Action<? super VcsMapping> rule, final Gradle gradle) {
        if (gradle.getParent() == null) {
            rootVcsMappings.add(rule);
        } else {
            if (!vcsMappings.containsKey(gradle)) {
                vcsMappings.put(gradle, Sets.<Action<? super VcsMapping>>newLinkedHashSet());
            }
            vcsMappings.get(gradle).add(rule);
        }
    }
}
