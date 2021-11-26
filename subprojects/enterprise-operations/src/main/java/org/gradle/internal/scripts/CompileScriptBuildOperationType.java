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

package org.gradle.internal.scripts;

import org.gradle.internal.operations.BuildOperationType;

/**
 * Details about a build script compilation.
 *
 * @since 4.10
 */
public class CompileScriptBuildOperationType implements BuildOperationType<CompileScriptBuildOperationType.Details, CompileScriptBuildOperationType.Result> {

    public interface Details {
        /**
         * The build script backing language.
         * The language should be upper case. E.g GROOVY
         * */
        String getLanguage();

        /**
         * The compile stage as a descriptive String.
         * Build scripts can be processed in multiple stages, depending on the language.
         * Groovy backed build scripts are processed in two stages.
         * Kotlin backed build scripts are processed in three stages
         * */
        String getStage();
    }

    public interface Result {
    }

}
