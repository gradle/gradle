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

import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.Cast;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VcsMappings;

public class DefaultVcsMappings implements VcsMappings {
    private final VcsMappingsStore vcsMappings;
    private final Gradle gradle;

    public DefaultVcsMappings(VcsMappingsStore vcsMappings, Gradle gradle) {
        this.vcsMappings = vcsMappings;
        this.gradle = gradle;
    }

    @Override
    public VcsMappings all(Action<? super VcsMapping> rule) {
        vcsMappings.addRule(rule, gradle);
        return this;
    }

    @Override
    public VcsMappings withModule(String module, Action<? super VcsMapping> rule) {
        vcsMappings.addRule(new GavFilteredRule(module, rule), gradle);
        return this;
    }

    private static class GavFilteredRule implements Action<VcsMapping> {
        private final String groupName;
        private final Action<? super VcsMapping> delegate;

        private GavFilteredRule(String groupName, Action<? super VcsMapping> delegate) {
            this.groupName = groupName;
            this.delegate = delegate;
        }

        @Override
        public void execute(VcsMapping mapping) {
            if (mapping.getRequested() instanceof ModuleComponentSelector) {
                ModuleComponentSelector moduleComponentSelector = Cast.uncheckedCast(mapping.getRequested());
                // TODO - should use a notation parser to parse the provided string instead
                if (groupName.equals(moduleComponentSelector.getGroup() + ":" + moduleComponentSelector.getModule())) {
                    delegate.execute(mapping);
                }
            }
        }
    }
}
