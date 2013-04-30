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

package org.gradle.buildsetup.tasks

import org.gradle.api.Incubating
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.tasks.OutputFile

import javax.inject.Inject

@Incubating
class GenerateBuildFile extends TextFileGenerationTask {
    private final DocumentationRegistry documentationRegistry

    @OutputFile
    File buildFile

    @Inject
    public GenerateBuildFile(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
        templateURL = GenerateBuildFile.class.getResource("/org/gradle/buildsetup/tasks/templates/build.gradle.template")
    }

    @Override
    protected File getOutputFile() {
        return getBuildFile()
    }

    @Override
    protected Map getTemplateBindings(){
        return [ref_userguide_java_tutorial:documentationRegistry.getDocumentationFor("tutorial_java_projects")]
    }
}
