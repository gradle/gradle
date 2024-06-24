/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.problems.internal.Problem;

import java.util.Collection;
import java.util.Collections;

public class ExceptionWithProblems extends RuntimeException {

    private final Collection<Problem> problems;

    public ExceptionWithProblems(Throwable throwable, Collection<Problem> problems) {
        super(throwable);
        this.problems = Collections.unmodifiableCollection(problems);
    }

    public Collection<Problem> getProblems() {
        return problems;
    }

    public static ExceptionWithProblems of(Throwable throwable, Collection<Problem> problems) {
        return new ExceptionWithProblems(throwable, problems);
    }

}
