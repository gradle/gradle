/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.internal.Actions;

/**
 * A service that injects dependency substitution rules into the build.
 */
public interface DependencySubstitutionRules {
    DependencySubstitutionRules NO_OP = new DependencySubstitutionRules() {
        @Override
        public Action<DependencySubstitution> getRuleAction() {
            return Actions.doNothing();
        }

        @Override
        public boolean rulesMayAddProjectDependency() {
            return false;
        }
    };

    Action<DependencySubstitution> getRuleAction();

    boolean rulesMayAddProjectDependency();
}
