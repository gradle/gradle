/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.internal.operations.GradleExceptionWithContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Problems {

    private static final ThreadLocal<List<Object>> problems = new ThreadLocal<List<Object>>();
    public static List<Object> removeAllProblems() {
        return problems.get();
    }

    public static void reportWarning(String message) {
        Map<String, String> warning = new HashMap<String, String>();
        warning.put("severity", "WARNING");
        warning.put("message", message);
        addProblem(warning);
    }

    public static void reportFailure(Object failureContext, Throwable cause) {
        addProblem(failureContext);
        throw new GradleExceptionWithContext(failureContext, cause);
    }

    private static void addProblem(Object problem) {
        List<Object> problemList = problems.get();
        if (problemList == null) {
            problemList = new ArrayList<Object>();
        }
        problemList.add(problem);
        problems.set(problemList);
    }
}
