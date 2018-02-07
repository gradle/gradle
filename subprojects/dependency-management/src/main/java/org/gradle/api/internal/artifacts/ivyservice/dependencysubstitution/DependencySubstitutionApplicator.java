/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.internal.component.model.DependencyMetadata;

/**
 * A dependency substitution applicator is responsible for applying substitution rules to dependency metadata.
 * Substitution result may either be the same module (no substitution), a different module (target of the substitution
 * is going to be different) or a failure.
 */
public interface DependencySubstitutionApplicator {
    SubstitutionResult apply(DependencyMetadata dependency);

    class SubstitutionResult {
        private final DependencySubstitutionInternal result;
        private final Throwable failure;

        private SubstitutionResult(DependencySubstitutionInternal result, Throwable failure) {
            this.result = result;
            this.failure = failure;
        }

        public static SubstitutionResult of(DependencySubstitutionInternal details) {
            return new SubstitutionResult(details, null);
        }

        public static SubstitutionResult failed(Throwable err) {
            return new SubstitutionResult(null, err);
        }

        public DependencySubstitutionInternal getResult() {
            return result;
        }

        public Throwable getFailure() {
            return failure;
        }

        public boolean hasFailure() {
            return failure != null;
        }
    }
}
