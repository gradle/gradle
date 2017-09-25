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
import org.gradle.internal.Actions;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VcsMappings;
import org.gradle.vcs.VersionControlSpec;

public interface VcsMappingsInternal extends VcsMappings {
    Action<VcsMapping> getVcsMappingRule();
    boolean hasRules();

    VcsMappingsInternal NO_OP = new VcsMappingsInternal() {
        @Override
        public Action<VcsMapping> getVcsMappingRule() {
            return Actions.doNothing();
        }

        @Override
        public boolean hasRules() {
            return false;
        }

        @Override
        public VcsMappings addRule(String message, Action<VcsMapping> rule) {
            return this;
        }

        @Override
        public VcsMappings withModule(String groupName, Action<VcsMapping> rule) {
            return this;
        }

        @Override
        public <T extends VersionControlSpec> T vcs(Class<T> type, Action<? super T> configuration) {
            return null;
        }
    };
}
