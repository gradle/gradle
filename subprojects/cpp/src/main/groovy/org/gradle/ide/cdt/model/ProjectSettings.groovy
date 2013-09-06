/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.ide.cdt.model

import org.gradle.api.Incubating

/**
 * Gradle model element, the configurable parts of the .project file.
 */
@Incubating
class ProjectSettings {
    String name

    /**
     * Apply this logical model to the physical descriptor
     */
    void applyTo(ProjectDescriptor descriptor) {
        descriptor.getOrCreate("name").value = name

        // not anywhere close to right at all, very hardcoded.
        def genMakeBuilderBuildCommand = descriptor.findBuildCommand { it.name[0].text() == "org.eclipse.cdt.managedbuilder.core.genmakebuilder" }
        if (genMakeBuilderBuildCommand) {
            def dict = genMakeBuilderBuildCommand.arguments[0].dictionary.find { it.key[0].text() == "org.eclipse.cdt.make.core.buildLocation" }
            if (dict) {
                dict.value[0].value = "\${workspace_loc:/$name/Debug}"
            }
        }

    }
}