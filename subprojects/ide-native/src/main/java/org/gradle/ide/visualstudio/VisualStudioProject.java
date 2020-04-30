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

import org.gradle.api.Buildable;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.HasInternalProtocol;

/**
 * A visual studio project, created from one or more native binaries.
 *
 * <p>
 *
 * The content and location of the generate project file can be modified by the supplied methods:
 *
 * <pre class='autoTested'>
 *  plugins {
 *      id 'cpp'
 *      id 'visual-studio'
 *  }
 *
 *  model {
 *      visualStudio {
 *          projects.all {
 *              projectFile.location = "vs/${name}.vcxproj"
 *              projectFile.withXml {
 *                  asNode().appendNode('PropertyGroup', [Label: 'Custom'])
 *                          .appendNode('ProjectDetails', "Project is named ${project.name}")
 *              }
 *          }
 *      }
 *  }
 * </pre>
 */
@HasInternalProtocol
public interface VisualStudioProject extends Named, Buildable {
    /**
     * Configuration for the generated project file.
     */
    @Internal
    @Incubating
    XmlConfigFile getProjectFile();

    /**
     * Configuration for the generated filters file.
     */
    @Internal
    @Incubating
    XmlConfigFile getFiltersFile();

    @Override
    @Internal
    @Incubating
    TaskDependency getBuildDependencies();

    @Override
    @Input
    String getName();
}
