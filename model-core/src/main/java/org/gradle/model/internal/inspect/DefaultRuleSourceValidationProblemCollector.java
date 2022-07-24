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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class DefaultRuleSourceValidationProblemCollector implements RuleSourceValidationProblemCollector {
    private final ValidationProblemCollector collector;

    public DefaultRuleSourceValidationProblemCollector(ValidationProblemCollector collector) {
        this.collector = collector;
    }

    @Override
    public boolean hasProblems() {
        return collector.hasProblems();
    }

    @Override
    public void add(String problem) {
        collector.add(problem);
    }

    @Override
    public void add(Field field, String problem) {
        collector.add(field, problem);
    }

    @Override
    public void add(MethodRuleDefinition<?, ?> method, String problem) {
        add(method.getMethod().getMethod(), problem);
    }

    @Override
    public void add(MethodRuleDefinition<?, ?> method, String problem, Throwable cause) {
        add(method.getMethod().getMethod(), problem + ": " + cause.getMessage());
    }

    @Override
    public void add(Method method, String problem) {
        add(method, "rule", problem);
    }

    @Override
    public void add(Method method, String role, String problem) {
        collector.add(method, role, problem);
    }

    @Override
    public void add(Constructor<?> constructor, String problem) {
        collector.add(constructor, problem);
    }
}
