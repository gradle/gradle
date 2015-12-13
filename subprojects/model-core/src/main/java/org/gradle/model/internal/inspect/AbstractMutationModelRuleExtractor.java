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

import org.gradle.api.Nullable;
import org.gradle.model.internal.core.ExtractedModelAction;
import org.gradle.model.internal.core.ExtractedModelRule;
import org.gradle.model.internal.core.ModelActionRole;

import java.lang.annotation.Annotation;

public abstract class AbstractMutationModelRuleExtractor<T extends Annotation> extends AbstractAnnotationDrivenModelRuleExtractor<T> {
    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        validateIsVoidMethod(ruleDefinition, context);
        if (ruleDefinition.getReferences().isEmpty()) {
            context.add(ruleDefinition, "A method " + getDescription() + " must have at least one parameter");
        }
        if (context.hasProblems()) {
            return null;
        }
        return new ExtractedModelAction(getMutationType(), new MethodBackedModelAction<S>(ruleDefinition));
    }

    protected abstract ModelActionRole getMutationType();
}
