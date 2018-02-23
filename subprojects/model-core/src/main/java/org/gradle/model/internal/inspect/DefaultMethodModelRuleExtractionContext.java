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

package org.gradle.model.internal.inspect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

class DefaultMethodModelRuleExtractionContext implements MethodModelRuleExtractionContext {
    private final ModelRuleExtractor extractor;
    private final RuleSourceValidationProblemCollector problems;

    public DefaultMethodModelRuleExtractionContext(ModelRuleExtractor extractor, ValidationProblemCollector problems) {
        this.extractor = extractor;
        this.problems = new DefaultRuleSourceValidationProblemCollector(problems);
    }

    @Override
    public ModelRuleExtractor getRuleExtractor() {
        return extractor;
    }

    @Override
    public boolean hasProblems() {
        return problems.hasProblems();
    }

    @Override
    public void add(Field field, String problem) {
        problems.add(field, problem);
    }

    @Override
    public void add(Method method, String problem) {
        problems.add(method, problem);
    }

    @Override
    public void add(Method method, String role, String problem) {
        problems.add(method, role, problem);
    }

    @Override
    public void add(Constructor<?> constructor, String problem) {
        problems.add(constructor, problem);
    }

    @Override
    public void add(MethodRuleDefinition<?, ?> method, String problem) {
        problems.add(method, problem);
    }

    @Override
    public void add(MethodRuleDefinition<?, ?> method, String problem, Throwable cause) {
        problems.add(method, problem, cause);
    }

    @Override
    public void add(String problem) {
        problems.add(problem);
    }
}
