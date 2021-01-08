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

package org.gradle.model.internal.inspect;

import org.gradle.model.Each;
import org.gradle.model.Path;

import java.lang.annotation.Annotation;
import java.util.List;

public enum RuleApplicationScope {
    /**
     * Rule should be applied to the scope element only.
     */
    SELF,

    /**
     * Rule should be applied to the all matching descendant elements of the scope.
     */
    DESCENDANTS;

    /**
     * Detects if the subject of the rule has been annotated with {@literal @}{@link Each}.
     *
     * @throws IndexOutOfBoundsException If the rule definition has too few parameters.
     */
    public static RuleApplicationScope fromRuleDefinition(RuleSourceValidationProblemCollector problems, MethodRuleDefinition<?, ?> ruleDefinition, int subjectParamIndex) {
        List<List<Annotation>> parameterAnnotations = ruleDefinition.getParameterAnnotations();
        if (subjectParamIndex >= parameterAnnotations.size()) {
            throw new IndexOutOfBoundsException("Rule definition should have at least " + (subjectParamIndex + 1) + " parameters");
        }
        RuleApplicationScope result = null;
        for (int paramIndex = 0; paramIndex < parameterAnnotations.size(); paramIndex++) {
            List<Annotation> annotations = parameterAnnotations.get(paramIndex);
            boolean annotatedWithEach = hasAnnotation(annotations, Each.class);
            if (paramIndex == subjectParamIndex) {
                if (annotatedWithEach && hasAnnotation(annotations, Path.class)) {
                    problems.add(ruleDefinition, "Rule subject must not be annotated with both @Path and @Each.");
                }
                result = annotatedWithEach ? DESCENDANTS : SELF;
            } else if (annotatedWithEach) {
                problems.add(ruleDefinition, String.format("Rule parameter #%d should not be annotated with @Each.", paramIndex + 1));
            }
        }
        assert result != null;
        return result;
    }

    private static boolean hasAnnotation(Iterable<Annotation> annotations, Class<? extends Annotation> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }
}
