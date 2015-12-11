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

import org.gradle.internal.reflect.MethodDescription;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ValidationProblemCollector {
    private final ModelType<?> source;
    private final List<String> problems = new ArrayList<String>();

    public ValidationProblemCollector(ModelType<?> source) {
        this.source = source;
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    public void add(String problem) {
        problems.add(problem);
    }

    public void add(MethodRuleDefinition<?, ?> method, String problem) {
        StringBuilder sb = new StringBuilder();
        method.getDescriptor().describeTo(sb);
        problems.add("Method " + sb + " is not a valid rule method: " + problem);
    }

    public void add(Method method, String problem) {
        String description = MethodDescription.name(method.getName())
                .takes(method.getGenericParameterTypes())
                .toString();
        problems.add("Method " + description + " is not a valid rule method: " + problem);
    }

    public String format() {
        StringBuilder errorString = new StringBuilder(String.format("Type %s is not a valid rule source:", source));
        if (problems.size() == 1 && errorString.length() + problems.get(0).length() < 80) {
            errorString.append(' ');
            errorString.append(problems.get(0));
        } else {
            for (String problem : problems) {
                errorString.append(String.format("\n- %s", problem));
            }
        }
        return errorString.toString();
    }
}
