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
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.Actions;
import org.gradle.util.CollectionUtils;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VersionControlSpec;

import java.util.Map;
import java.util.Set;

public class DefaultVcsMappingsStore implements VcsMappingsStore {
    private final Set<Action<? super VcsMapping>> rootVcsMappings = Sets.newLinkedHashSet();
    private final Map<Gradle, Set<Action<? super VcsMapping>>> vcsMappings = Maps.newHashMap();

    @Override
    public Action<VcsMapping> getVcsMappingRule() {
        return new Action<VcsMapping>() {
            @Override
            public void execute(VcsMapping vcsMapping) {
                VcsMappingInternal vcsMappingInternal = (VcsMappingInternal) vcsMapping;
                Actions.composite(rootVcsMappings).execute(vcsMappingInternal);
                if (!vcsMappingInternal.hasRepository()) {
                    Set<VersionControlSpec> resolutions = Sets.newHashSet();
                    for (Gradle gradle : vcsMappings.keySet()) {
                        Actions.composite(vcsMappings.get(gradle)).execute(vcsMappingInternal);
                        if (vcsMappingInternal.hasRepository()) {
                            resolutions.add(vcsMappingInternal.getRepository());
                        }
                    }
                    if (resolutions.size() > 1) {
                        Set<String> resolutionDisplayNames = CollectionUtils.collect(resolutions, new Transformer<String, VersionControlSpec>() {
                            @Override
                            public String transform(VersionControlSpec versionControlSpec) {
                                return versionControlSpec.getDisplayName();
                            }
                        });
                        throw new GradleException("Conflicting external source dependency rules were found in nested builds for " + vcsMappingInternal.getRequested().getDisplayName() + ":\n  " + CollectionUtils.join("\n  ", resolutionDisplayNames));
                    }
                }
            }
        };
    }

    @Override
    public boolean hasRules() {
        return !(vcsMappings.isEmpty() && rootVcsMappings.isEmpty());
    }

    @Override
    public void addRule(final Action<? super VcsMapping> rule, final Gradle gradle) {
        // TODO: Hacky hook to ensure we have the classloader scope from the appropriate Gradle instance.
        // Detangle the "build definition" part of the VcsMapping from the rule itself so this classloader can
        // be carried around by it.
        Action<VcsMapping> classloaderWrapping = new Action<VcsMapping>() {
            @Override
            public void execute(VcsMapping vcsMapping) {
                ((DefaultVcsMapping)vcsMapping).setClassLoaderScope(((GradleInternal)gradle).getSettings().getClassLoaderScope());
                rule.execute(vcsMapping);
            }
        };
        if (gradle.getParent() == null) {
            rootVcsMappings.add(classloaderWrapping);
        } else {
            if (!vcsMappings.containsKey(gradle)) {
                vcsMappings.put(gradle, Sets.<Action<? super VcsMapping>>newLinkedHashSet());
            }
            vcsMappings.get(gradle).add(classloaderWrapping);
        }
    }
}
