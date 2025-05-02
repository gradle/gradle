/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.buildinit.plugins.internal;

import org.gradle.internal.Factory;

import java.util.List;

public class ConditionalTemplateOperation implements TemplateOperation {
    private final List<TemplateOperation> optionalOperations;
    private final Factory<Boolean> condition;

    public ConditionalTemplateOperation(Factory<Boolean> precondition, List<TemplateOperation> optionalOperations) {
        this.condition = precondition;
        this.optionalOperations = optionalOperations;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void generate() {
        if (condition.create()) {
            for (TemplateOperation operation : optionalOperations) {
                operation.generate();
            }
        }
    }
}
