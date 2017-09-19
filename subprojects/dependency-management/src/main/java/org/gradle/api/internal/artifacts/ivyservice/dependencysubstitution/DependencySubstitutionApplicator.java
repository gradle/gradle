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

public interface DependencySubstitutionApplicator {
    Application apply(DependencyMetadata dependency);

    class Application {
        private final DependencySubstitutionInternal result;
        private final Throwable failure;

        private Application(DependencySubstitutionInternal result, Throwable failure) {
            this.result = result;
            this.failure = failure;
        }

        public static Application of(DependencySubstitutionInternal details) {
            return new Application(details, null);
        }

        public static Application of(Throwable err) {
            return new Application(null, err);
        }

        public DependencySubstitutionInternal getResult() {
            return result;
        }

        public Throwable getFailure() {
            return failure;
        }
    }
}
