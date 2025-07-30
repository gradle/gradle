/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel;

class MyCustomModel implements Serializable {
    List<ProjectIdentifier> projectIdentifiers;
    List<String> paths;
    Map<File, KotlinDslScriptModel> scriptModels;
    GradleBuild build

    MyCustomModel(Map<File, KotlinDslScriptModel> models,
                  List<ProjectIdentifier> projectIdentifiers,
                  List<String> paths,
                  GradleBuild build) {
        this.build = build
        this.projectIdentifiers = projectIdentifiers;
        this.paths = paths;
        this.scriptModels = models;
    }
}
