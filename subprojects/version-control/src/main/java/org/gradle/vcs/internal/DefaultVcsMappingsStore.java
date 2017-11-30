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

import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.vcs.VcsMapping;

import java.util.Set;

public class DefaultVcsMappingsStore implements VcsMappingsStore {
    private final Set<Action<VcsMapping>> vcsMappings = Sets.newHashSet();
    private final Set<Action<VcsMapping>> rootVcsMappings = Sets.newHashSet();

    @Override
    public Action<VcsMapping> getVcsMappingRule() {
        return new Action<VcsMapping>() {
            @Override
            public void execute(VcsMapping vcsMapping) {
                VcsMappingInternal vcsMappingInternal = (VcsMappingInternal) vcsMapping;
                Actions.composite(rootVcsMappings).execute(vcsMappingInternal);
                if (!vcsMappingInternal.hasRepository()) {
                    Actions.composite(vcsMappings).execute(vcsMappingInternal);
                }
            }
        };
    }

    @Override
    public boolean hasRules() {
        return !(vcsMappings.isEmpty() && rootVcsMappings.isEmpty());
    }

    @Override
    public void addRule(Action<VcsMapping> rule, boolean isRootBuild) {
        if (isRootBuild) {
            rootVcsMappings.add(rule);
        } else {
            vcsMappings.add(rule);
        }
    }
}
