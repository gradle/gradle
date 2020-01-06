/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.models;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;


public interface KotlinBuildScriptModel {

    String SCRIPT_GRADLE_PROPERTY_NAME = "org.gradle.kotlin.dsl.provider.script";

    List<File> getClassPath();

    List<File> getSourcePath();

    List<String> getImplicitImports();

    List<EditorReport> getEditorReports();

    List<String> getExceptions();

    /**
     * The directory of the project in which the script was found.
     */
    @Nullable
    File getEnclosingScriptProjectDir();
}
