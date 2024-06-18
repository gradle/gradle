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

package org.gradle.tooling.events.problems.internal;

import org.gradle.tooling.events.problems.Solution;

import java.util.Objects;

public class DefaultSolution implements Solution {

    private final String solution;

    public DefaultSolution(String solution) {
        this.solution = solution;
    }

    @Override
    public String getSolution() {
        return solution;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultSolution that = (DefaultSolution) o;
        return Objects.equals(solution, that.solution);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(solution);
    }
}
