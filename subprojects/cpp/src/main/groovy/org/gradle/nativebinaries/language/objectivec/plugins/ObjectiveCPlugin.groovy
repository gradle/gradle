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
package org.gradle.nativebinaries.language.objectivec.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.nativebinaries.toolchain.internal.plugins.StandardToolChainsPlugin

/**
 * A plugin for projects wishing to build native binary components from Objective-C sources.
 *
 * <p>Adds core tool chain support to the {@link ObjectiveCNativeBinariesPlugin}.</p>
 */
@Incubating
class ObjectiveCPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(StandardToolChainsPlugin)
        project.plugins.apply(ObjectiveCNativeBinariesPlugin)
    }

}