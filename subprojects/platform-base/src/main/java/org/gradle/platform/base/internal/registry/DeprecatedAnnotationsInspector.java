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

package org.gradle.platform.base.internal.registry;

import org.gradle.api.Nullable;
import org.gradle.model.internal.inspect.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodModelRuleExtractionContext;
import org.gradle.model.internal.inspect.MethodModelRuleExtractor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.platform.base.ComponentType;

public class DeprecatedAnnotationsInspector implements MethodModelRuleExtractor {

    @SuppressWarnings("deprecation")
    @Override
    public boolean isSatisfiedBy(MethodRuleDefinition<?, ?> definition) {
        return definition.isAnnotationPresent(org.gradle.platform.base.BinaryType.class)
            || definition.isAnnotationPresent(org.gradle.platform.base.LanguageType.class);
    }

    @Override
    public String getDescription() {
        return "annotated with no longer supported BinaryType or LanguageType annotations";
    }

    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        context.add(ruleDefinition, deprecationMessageFor(ruleDefinition));
        return null;
    }

    @SuppressWarnings("deprecation")
    private <R, S> String deprecationMessageFor(MethodRuleDefinition<R, S> ruleDefinition) {
        Class<?> annotation = ruleDefinition.isAnnotationPresent(org.gradle.platform.base.BinaryType.class) ? org.gradle.platform.base.BinaryType.class : org.gradle.platform.base.LanguageType.class;
        return String.format("Annotation %s is no longer supported. Please replace it with %s.", annotation.getSimpleName(), ComponentType.class.getSimpleName());
    }
}
