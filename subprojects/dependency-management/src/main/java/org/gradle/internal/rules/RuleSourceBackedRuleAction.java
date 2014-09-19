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

package org.gradle.internal.rules;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.Mutate;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.List;

public class RuleSourceBackedRuleAction<T> implements RuleAction<T> {
    private final MethodRuleDefinition<Void> methodRuleDefinition;

    private RuleSourceBackedRuleAction(MethodRuleDefinition<Void> methodRuleDefinition) {
        this.methodRuleDefinition = methodRuleDefinition;
    }

    public static <T> RuleSourceBackedRuleAction<T> create(Class<?> ruleSource, ModelType<T> subjectType) {
        List<Method> mutateMethods = JavaReflectionUtil.findAllMethods(ruleSource, new Spec<Method>() {
            public boolean isSatisfiedBy(Method element) {
                return element.isAnnotationPresent(Mutate.class);
            }
        });
        if (mutateMethods.size() != 1) {
            throw invalid(ruleSource, "must have at exactly one method annotated with @Mutate");
        }
        Method ruleMethod = mutateMethods.get(0);
        if (ruleMethod.getReturnType() != Void.TYPE) {
            throw invalid(ruleSource, "rule method must return void");
        }
        MethodRuleDefinition<Void> ruleDefinition = DefaultMethodRuleDefinition.create(ruleSource, ruleMethod);
        List<ModelReference<?>> references = ruleDefinition.getReferences();
        if (references.size() == 0) {
            throw invalid(ruleSource, "rule method must have at least one parameter");
        }
        if (!references.get(0).getType().equals(subjectType)) {
            throw invalid(ruleSource, String.format("first parameter of rule method must be of type %s", subjectType));
        }
        return new RuleSourceBackedRuleAction<T>(ruleDefinition);
    }

    private static RuntimeException invalid(Class<?> source, String reason) {
        return new InvalidModelRuleDeclarationException("Type " + source.getName() + " is not a valid model rule source: " + reason);
    }

    public List<Class<?>> getInputTypes() {
        List<ModelReference<?>> references = methodRuleDefinition.getReferences();
        return CollectionUtils.collect(references.subList(1, references.size()), new Transformer<Class<?>, ModelReference<?>>() {
            public Class<?> transform(ModelReference<?> modelReference) {
                return modelReference.getType().getRawClass();
            }
        });
    }

    public void execute(T subject, List<?> inputs) {
        List<Object> args = Lists.newArrayList();
        args.add(subject);
        args.addAll(inputs);
        methodRuleDefinition.getRuleInvoker().invoke(args.toArray());
    }
}
