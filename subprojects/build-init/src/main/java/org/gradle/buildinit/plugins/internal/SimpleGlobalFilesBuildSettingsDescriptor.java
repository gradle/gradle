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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.internal.file.PathToFileResolver;

public class SimpleGlobalFilesBuildSettingsDescriptor implements ProjectInitDescriptor {

    private final PathToFileResolver fileResolver;
    private final DocumentationRegistry documentationRegistry;

    public SimpleGlobalFilesBuildSettingsDescriptor(PathToFileResolver fileResolver, DocumentationRegistry documentationRegistry) {
        this.fileResolver = fileResolver;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void generate(BuildInitDsl dsl, BuildInitTestFramework testFramework) {
        new BuildScriptBuilder(dsl, fileResolver, "settings")
            .fileComment(
                "The settings file is used to specify which projects to include in your build.\n\n"
                    + "Detailed information about configuring a multi-project build in Gradle can be found\n"
                    + "in the user guide at " + documentationRegistry.getDocumentationFor("multi_project_builds"))
            .propertyAssignment(null, "rootProject.name", fileResolver.resolve(".").getName())
            .create()
            .generate();
    }

    @Override
    public boolean supports(BuildInitDsl dsl) {
        return true;
    }

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return false;
    }
}
