/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.BuildableModelElement;
import org.gradle.nativeplatform.NativeComponentSpec;

import java.util.Set;

/**
 * A visual studio solution, representing one or more {@link org.gradle.nativeplatform.NativeBinarySpec} instances
 * from the same {@link org.gradle.nativeplatform.NativeComponentSpec}.
 * <p/>
 *
 * The content and location of the generate solution file can be modified by the supplied methods:
 *
 * <pre autoTested="true">
 *  apply plugin: "visual-studio"
 *  model {
 *      visualStudio {
 *          solutions.all {
 *              solutionFile.location = "vs/${name}.sln"
 *              solutionFile.withContent { TextProvider content ->
 *                  content.asBuilder().insert(0, "# GENERATED FILE: DO NOT EDIT\n")
 *                  content.text = content.text.replaceAll("HideSolutionNode = FALSE", "HideSolutionNode = TRUE")
 *              }
 *          }
 *      }
 *  }
 * </pre>
 */
@Incubating
public interface VisualStudioSolution extends Named, BuildableModelElement {
    /**
     * The set of projects included in this solution.
     */
    Set<VisualStudioProject> getProjects();

    /**
     * The component that this solution represents.
     */
    NativeComponentSpec getComponent();

    /**
     * Configuration for the generated solution file.
     */
    TextConfigFile getSolutionFile();
}
