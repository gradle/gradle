/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.report.unbound;

import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.ArrayList;
import java.util.List;

public class UnboundRule {

    private final ModelRuleDescriptor descriptor;

    private final List<UnboundRuleInput> immutableInputs;
    private final List<UnboundRuleInput> mutableInputs;

    public UnboundRule(ModelRuleDescriptor descriptor, List<UnboundRuleInput> immutableInputs, List<UnboundRuleInput> mutableInputs) {
        this.descriptor = descriptor;
        this.immutableInputs = immutableInputs;
        this.mutableInputs = mutableInputs;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    public List<UnboundRuleInput> getImmutableInputs() {
        return immutableInputs;
    }

    public List<UnboundRuleInput> getMutableInputs() {
        return mutableInputs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ModelRuleDescriptor descriptor;
        private final List<UnboundRuleInput> immutableInputs = new ArrayList<UnboundRuleInput>();
        private final List<UnboundRuleInput> mutableInputs = new ArrayList<UnboundRuleInput>();

        public Builder descriptor(ModelRuleDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public Builder immutableInput(UnboundRuleInput.Builder inputBuilder) {
            immutableInputs.add(inputBuilder.build());
            return this;
        }

        public Builder mutableInput(UnboundRuleInput.Builder inputBuilder) {
            mutableInputs.add(inputBuilder.build());
            return this;
        }

        public UnboundRule build() {
            return new UnboundRule(descriptor, immutableInputs, mutableInputs);
        }
    }
}
