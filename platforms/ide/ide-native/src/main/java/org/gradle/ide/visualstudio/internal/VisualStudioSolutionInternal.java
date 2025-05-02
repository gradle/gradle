/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio.internal;

import org.gradle.ide.visualstudio.VisualStudioSolution;

import java.util.List;

public interface VisualStudioSolutionInternal extends VisualStudioSolution {
    /**
     * The list of project artifacts included in this solution.
     */
    List<VisualStudioProjectMetadata> getProjects();

    /**
     * Adds tasks required to build this component.
     */
    void builtBy(Object... tasks);
}
