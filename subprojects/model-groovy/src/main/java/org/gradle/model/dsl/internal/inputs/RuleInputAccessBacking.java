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

package org.gradle.model.dsl.internal.inputs;

import com.google.common.collect.ImmutableMap;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelRuleInput;

public abstract class RuleInputAccessBacking {

    private RuleInputAccessBacking() {
    }

    private static final ThreadLocal<ImmutableMap<String, Object>> INPUT = new ThreadLocal<ImmutableMap<String, Object>>();

    public static void runWithContext(Inputs inputs, Runnable runnable) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (ModelRuleInput<?> ruleInput : inputs.getRuleInputs()) {
            assert ruleInput.getBinding().getReference().isUntyped(); // We are relying on inputs being untyped
            builder.put(ruleInput.getBinding().getPath().toString(), ruleInput.getView().getInstance());
        }

        ImmutableMap<String, Object> inputsMap = builder.build();
        INPUT.set(inputsMap);
        try {
            runnable.run();
        } finally {
            INPUT.remove();
        }
    }

    public static RuleInputAccess getAccess() {
        final ImmutableMap<String, Object> inputs = INPUT.get();
        return new RuleInputAccess() {
            public Object input(String modelPath) {
                return inputs.get(modelPath);
            }
        };
    }

}
