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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.ProblemGroup;

public abstract class GradleCoreProblemGroup implements ProblemGroup {

    private static final DefaultCompilationProblemGroup COMPILATION_PROBLEM_GROUP = new DefaultCompilationProblemGroup("compilation", "Compilation");
    private static final DefaultProblemGroup DEPRECATION_PROBLEM_GROUP = new DefaultProblemGroup("deprecation", "Deprecation");
    private static final DefaultValidationProblemGroup VALIDATION_PROBLEM_GROUP = new DefaultValidationProblemGroup("validation", "Validation");
    private static final DefaultProblemGroup TASK_SELECTION_PROBLEM_GROUP = new DefaultProblemGroup("task-selection", "Task selection");
    private static final DefaultProblemGroup VERSION_CATALOG_PROBLEM_GROUP = new DefaultProblemGroup("dependency-version-catalog", "Version catalog");

    public static CompilationProblemGroup compilation() {
        return COMPILATION_PROBLEM_GROUP;
    }

    public static ProblemGroup deprecation() {
        return DEPRECATION_PROBLEM_GROUP;
    }

    public static ValidationProblemGroup validation() {
        return VALIDATION_PROBLEM_GROUP;
    }

    public static ProblemGroup taskSelection() {
        return TASK_SELECTION_PROBLEM_GROUP;
    }

    public static ProblemGroup versionCatalog() {
        return VERSION_CATALOG_PROBLEM_GROUP;
    }

    public interface CompilationProblemGroup extends ProblemGroup {
        ProblemGroup java();

        ProblemGroup groovy();

        ProblemGroup groovyDsl();
    }

    public interface ValidationProblemGroup extends ProblemGroup {
        ProblemGroup property();

        ProblemGroup type();
    }

    private static class DefaultCompilationProblemGroup extends DefaultProblemGroup implements CompilationProblemGroup {

        private final ProblemGroup java = new DefaultProblemGroup("java", "Java compilation", this);
        private final ProblemGroup groovy = new DefaultProblemGroup("groovy", "Groovy compilation", this);
        public ProblemGroup groovyDsl = new DefaultProblemGroup("groovy-dsl", "Groovy DSL script compilation", this);

        private DefaultCompilationProblemGroup(String id, String displayName) {
            super(id, displayName);
        }

        @Override
        public ProblemGroup java() {
            return this.java;
        }

        @Override
        public ProblemGroup groovy() {
            return this.groovy;
        }

        @Override
        public ProblemGroup groovyDsl() {
            return this.groovyDsl;
        }
    }

    private static class DefaultValidationProblemGroup extends DefaultProblemGroup implements ValidationProblemGroup {

        private final ProblemGroup property = new DefaultProblemGroup("property-validation", "Gradle property validation", this);

        private final ProblemGroup type = new DefaultProblemGroup("type-validation", "Gradle type validation", this);

        private DefaultValidationProblemGroup(String id, String displayName) {
            super(id, displayName);
        }

        @Override
        public ProblemGroup property() {
            return property;
        }

        @Override
        public ProblemGroup type() {
            return type;
        }
    }
}
