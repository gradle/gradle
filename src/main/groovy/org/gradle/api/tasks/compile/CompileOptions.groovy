/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile

/**
 * @author Hans Dockter
 */
class CompileOptions extends AbstractOptions {
    boolean failOnError = true
    boolean verbose = false
    boolean listFiles = false
    boolean deprecation = false
    boolean warnings = true
    String encoding = null
    boolean dependencyTracking = false
    boolean optimize

    boolean debug = true
    DebugOptions debugOptions = new DebugOptions()
    boolean fork = false
    ForkOptions forkOptions = new ForkOptions()

    String compiler = null
    boolean includeJavaRuntime = false
    String bootClasspath = null
    String extensionDirs = null

    List compilerArgs

    CompileOptions fork(Map forkArgs) {
        fork = true
        forkOptions.define(forkArgs)
        this
    }

    CompileOptions debug(Map debugArgs) {
        debug = true
        debugOptions.define(debugArgs)
        this
    }

    List excludedFieldsFromOptionMap() {
        ['debugOptions', 'forkOptions', 'compilerArgs']
    }

    Map fieldName2AntMap() {
        [
                warnings: 'nowarn',
                bootClasspath: 'bootclasspath',
                extensionDirs: 'extdirs',
                dependencyTracking: 'depend',
                failOnError: 'failonerror',
                listFiles: 'listfiles',
        ]
    }

    Map fieldValue2AntMap() {
        [
                warnings: {!warnings}
        ]
    }

    Map optionMap() {
        super.optionMap() + forkOptions.optionMap() + debugOptions.optionMap()
    }

}

