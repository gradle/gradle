/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.dependencies;

import org.gradle.api.artifacts.result.ResolvedDependencyResult;

import java.util.Comparator;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId;

/**
 * by Szczepan Faber, created at: 8/22/12
 */
public class DependencyComparator implements Comparator<ResolvedDependencyResult> {
    public int compare(ResolvedDependencyResult left, ResolvedDependencyResult right) {
        //TODO SF push out to some Sorter object in core, needs to be reused. Unit test coverage.
        if (left.getSelected().getId().equals(newId(left.getRequested()))) {
            return -1;
        } else if (right.getSelected().getId().equals(newId(right.getRequested()))) {
            return 1;
        }

        return left.getRequested().getVersion().compareTo(right.getRequested().getVersion());
    }
}
