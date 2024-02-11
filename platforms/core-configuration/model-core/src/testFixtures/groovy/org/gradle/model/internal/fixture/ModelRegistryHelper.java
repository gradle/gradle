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
package org.gradle.model.internal.fixture;

import org.gradle.api.internal.rules.RuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.registry.DefaultModelRegistry;
import org.gradle.model.internal.type.ModelType;

public class ModelRegistryHelper extends DefaultModelRegistry {
    public ModelRegistryHelper() {
        super(ProjectRegistrySpec.MODEL_RULE_EXTRACTOR, null);
    }

    public ModelRegistryHelper(ModelRuleExtractor ruleExtractor) {
        super(ruleExtractor, null);
    }

    public static <T> ModelType<RuleAwarePolymorphicNamedEntityInstantiator<T>> instantiatorType(Class<T> typeClass) {
        return new ModelType.Builder<RuleAwarePolymorphicNamedEntityInstantiator<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, ModelType.of(typeClass)).build();
    }
}
