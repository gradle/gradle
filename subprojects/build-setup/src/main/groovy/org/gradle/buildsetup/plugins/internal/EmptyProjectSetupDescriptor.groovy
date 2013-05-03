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

package org.gradle.buildsetup.plugins.internal

import org.gradle.api.Project
import org.gradle.api.internal.DocumentationRegistry

class EmptyProjectSetupDescriptor extends TemplateBasedProjectSetupDescriptor {

    public EmptyProjectSetupDescriptor(Project project,  DocumentationRegistry documentationRegistry) {
        super(project, documentationRegistry)
    }

    String getId() {
        return "empty"
    }

    @Override
    URL getBuildFileTemplate() {
        return EmptyProjectSetupDescriptor.class.getResource("/org/gradle/buildsetup/tasks/templates/build.gradle.template");
    }

    @Override
    URL getSettingsTemplate() {
        return EmptyProjectSetupDescriptor.class.getResource("/org/gradle/buildsetup/tasks/templates/settings.gradle.template")
    }
}
