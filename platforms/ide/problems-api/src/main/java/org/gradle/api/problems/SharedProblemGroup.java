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

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * A registry of shared problem groups.
 *
 * @since 8.8
 */
@Incubating
public abstract class SharedProblemGroup implements ProblemGroup {

    private SharedProblemGroup() {
    }

    private static final ProblemGroup GENERIC_PROBLEM_GROUP = new BasicProblemGroup("generic", "Generic");

    /**
     * A generic problem category. All problems IDs not configuring any group will be automatically use this group.
     *
     * @since 8.8
     */
    public static ProblemGroup generic() {
        return GENERIC_PROBLEM_GROUP;
    }

    private static class BasicProblemGroup implements ProblemGroup {
        private final String name;
        private final String displayName;

        BasicProblemGroup(String id, String displayName) {
            this.name = id;
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Nullable
        @Override
        public ProblemGroup getParent() {
            return null;
        }
    }
}
