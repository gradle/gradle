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

package org.gradle.model.internal.inspect;

import javax.annotation.Nullable;

public interface MethodModelRuleExtractor {
    boolean isSatisfiedBy(MethodRuleDefinition<?, ?> definition);

    String getDescription();

    /**
     * Should only be called when {@link #isSatisfiedBy(MethodRuleDefinition)} return true.
     *
     * @return The extracted rule, or null when there are problems with the rule definition. Problems should be collected using the given context.
     */
    @Nullable
    <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context);
}
