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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalProblemSummariesDetails;
import org.gradle.tooling.internal.protocol.InternalProblemSummary;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@NullMarked
public class DefaultProblemsSummariesDetails implements InternalProblemSummariesDetails, Serializable {

    private final List<InternalProblemSummary> problemIdCounts;

    public DefaultProblemsSummariesDetails(List<InternalProblemSummary> problemIdCounts) {
        this.problemIdCounts = problemIdCounts;
    }

    @Override
    public List<InternalProblemSummary> getProblemIdCounts() {
        return problemIdCounts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultProblemsSummariesDetails)) {
            return false;
        }
        DefaultProblemsSummariesDetails that = (DefaultProblemsSummariesDetails) o;
        return Objects.equals(problemIdCounts, that.problemIdCounts);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(problemIdCounts);
    }
}
