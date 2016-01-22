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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.gradle.model.Each;

import java.lang.annotation.Annotation;

public enum ChildTraversalType {
    /**
     * Rule should be applied to the scope element only.
     */
    SELF,

    /**
     * Rule should be applied to the all matching descendant elements of the scope.
     */
    DESCENDANTS;

    public static ChildTraversalType of(MethodRuleDefinition<?, ?> ruleDefinition, Iterable<Annotation> subjectAnnotations) {
        boolean each = Iterables.any(subjectAnnotations, new Predicate<Annotation>() {
            @Override
            public boolean apply(Annotation annotation) {
                return annotation instanceof Each;
            }
        });
        if (each) {
            return DESCENDANTS;
        } else {
            return SELF;
        }
    }
}
