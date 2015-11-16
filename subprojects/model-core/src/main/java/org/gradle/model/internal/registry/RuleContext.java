/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.registry;

import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import java.util.Deque;

public class RuleContext {

    private static final ThreadLocal<Deque<ModelRuleDescriptor>> STACK = new ThreadLocal<Deque<ModelRuleDescriptor>>() {
        @Override
        protected Deque<ModelRuleDescriptor> initialValue() {
            return Lists.newLinkedList();
        }
    };

    @Nullable
    public static ModelRuleDescriptor get() {
        return STACK.get().peek();
    }

    @Nullable
    public static ModelRuleDescriptor nest(ModelRuleDescriptor modelRuleDescriptor) {
        ModelRuleDescriptor parent = get();
        if (parent == null) {
            return modelRuleDescriptor;
        } else {
            return new NestedModelRuleDescriptor(parent, modelRuleDescriptor);
        }
    }

    @Nullable
    public static ModelRuleDescriptor nest(String modelRuleDescriptor) {
        return nest(new SimpleModelRuleDescriptor(modelRuleDescriptor));
    }

    public static void run(ModelRuleDescriptor descriptor, Runnable runnable) {
        STACK.get().push(descriptor);
        try {
            runnable.run();
        } finally {
            STACK.get().pop();
        }
    }
}
