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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.InsecureProtocolOption;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;

public class BuildScriptBuilderFactory {
    private final DocumentationRegistry documentationRegistry;

    public BuildScriptBuilderFactory(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    public BuildScriptBuilder scriptForNewProjects(BuildInitDsl dsl, BuildContentGenerationContext buildContentGenerationContext, String fileNameWithoutExtension, boolean useIncubatingAPIs) {
        return new BuildScriptBuilder(dsl, documentationRegistry, buildContentGenerationContext, fileNameWithoutExtension, useIncubatingAPIs, InsecureProtocolOption.FAIL, true);
    }

    public BuildScriptBuilder scriptForNewProjectsWithoutVersionCatalog(BuildInitDsl dsl, BuildContentGenerationContext buildContentGenerationContext, String fileNameWithoutExtension, boolean useIncubatingAPIs) {
        return new BuildScriptBuilder(dsl, documentationRegistry, buildContentGenerationContext, fileNameWithoutExtension, useIncubatingAPIs, InsecureProtocolOption.FAIL, false);
    }

    public BuildScriptBuilder scriptForMavenConversion(BuildInitDsl dsl, BuildContentGenerationContext buildContentGenerationContext, String fileNameWithoutExtension, boolean useIncubatingAPIs, InsecureProtocolOption insecureProtocolOption) {
        return new BuildScriptBuilder(dsl, documentationRegistry, buildContentGenerationContext, fileNameWithoutExtension, useIncubatingAPIs, insecureProtocolOption, true);
    }

    public BuildScriptBuilder scriptForMavenConversionWithoutVersionCatalog(BuildInitDsl dsl, BuildContentGenerationContext buildContentGenerationContext, String fileNameWithoutExtension, boolean useIncubatingAPIs, InsecureProtocolOption insecureProtocolOption) {
        return new BuildScriptBuilder(dsl, documentationRegistry, buildContentGenerationContext, fileNameWithoutExtension, useIncubatingAPIs, insecureProtocolOption, false);
    }
}
