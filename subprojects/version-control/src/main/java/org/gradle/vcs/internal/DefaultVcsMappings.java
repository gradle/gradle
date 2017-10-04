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
import org.gradle.api.Describable;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VcsMappings;
import org.gradle.vcs.VersionControlSpec;

import java.util.Set;

public class DefaultVcsMappings implements VcsMappingsInternal {
    private final Set<Action<VcsMapping>> vcsMappings;
    private final VersionControlSpecFactory versionControlSpecFactory;

    public DefaultVcsMappings(Instantiator instantiator) {
        this.versionControlSpecFactory = new VersionControlSpecFactory(instantiator);
        this.vcsMappings = Sets.newLinkedHashSet();
    }

    @Override
    public VcsMappings addRule(String message, Action<VcsMapping> rule) {
        vcsMappings.add(new DescribedRule(message, rule));
        return this;
    }

    @Override
    public VcsMappings withModule(String groupName, Action<VcsMapping> rule) {
        vcsMappings.add(new GavFilteredRule(groupName, rule));
        return this;
    }

    @Override
    public <T extends VersionControlSpec> T vcs(Class<T> type, Action<? super T> configuration) {
        T vcs = versionControlSpecFactory.create(type);
        configuration.execute(vcs);
        return vcs;
    }

    @Override
    public Action<VcsMapping> getVcsMappingRule() {
        return Actions.composite(vcsMappings);
    }

    @Override
    public boolean hasRules() {
        return !vcsMappings.isEmpty();
    }

    private static class DescribedRule implements Action<VcsMapping>, Describable {
        private final String displayName;
        private final Action<VcsMapping> delegate;

        private DescribedRule(String displayName, Action<VcsMapping> delegate) {
            this.displayName = displayName;
            this.delegate = delegate;
        }

        @Override
        public void execute(VcsMapping vcsMapping) {
            delegate.execute(vcsMapping);
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }
    }

    private static class GavFilteredRule extends DescribedRule {
        private final String groupName;

        private GavFilteredRule(String groupName, Action<VcsMapping> delegate) {
            super("filtered rule for module " + groupName, delegate);
            this.groupName = groupName;
        }

        @Override
        public void execute(VcsMapping mapping) {
            if (mapping.getRequested() instanceof ModuleComponentSelector) {
                ModuleComponentSelector moduleComponentSelector = Cast.uncheckedCast(mapping.getRequested());
                if (groupName.equals(moduleComponentSelector.getGroup() + ":" + moduleComponentSelector.getModule())) {
                    super.execute(mapping);
                }
            }
        }
    }
}
