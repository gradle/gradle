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

package org.gradle.ide.sync.fixtures

import org.gradle.internal.Pair
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher

import javax.annotation.Nullable

class HasConfigurationCacheProblemsInReportSpec {

    final List<Pair<Matcher<String>, String>> locationsWithProblems = []

    @Nullable
    Integer totalProblemsCount

    void validateSpec() {
        def totalCount = totalProblemsCount ?: locationsWithProblems.size()
        if (totalCount < locationsWithProblems.size()) {
            throw new IllegalArgumentException("Count of total problems can't be lesser than count of unique problems.")
        }
    }

    HasConfigurationCacheProblemsInReportSpec withLocatedProblem(String location, String problem) {
        withLocatedProblem(CoreMatchers.startsWith(location), problem)
        return this
    }

    HasConfigurationCacheProblemsInReportSpec withLocatedProblem(Matcher<String> location, String problem) {
        locationsWithProblems.add(Pair.of(location, problem))
        return this
    }

    HasConfigurationCacheProblemsInReportSpec withTotalProblemsCount(int totalProblemsCount) {
        this.totalProblemsCount = totalProblemsCount
        return this
    }
}
