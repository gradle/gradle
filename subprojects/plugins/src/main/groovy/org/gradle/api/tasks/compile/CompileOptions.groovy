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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

/**
 * @author Hans Dockter
 */
class CompileOptions extends AbstractOptions {
    /**
     * Specifies whether the compile task should fail when compilation fails. The default is {@code true}.
     */
    @Input
    boolean failOnError = true
    boolean verbose = false
    boolean listFiles = false

    /**
     * Specifies whether to log details of usage of deprecated members or classes. The default is {@code false}.
     */
    boolean deprecation = false

    /**
     * Specifies whether to log warning messages. The default is {@code true}.
     */
    boolean warnings = true

    /**
     * The source encoding name. Uses the platform default encoding if not specified. The default is {@code null}.
     */
    @Input @Optional
    String encoding = null
    @Input
    boolean optimize

    /**
     * Specifies whether debugging information should be included in the generated {@code .class} files. The default
     * is {@code true}.
     */
    @Input
    boolean debug = true

    /**
     * The options for debugging information generation.
     */
    @Nested
    DebugOptions debugOptions = new DebugOptions()

    /**
     * Specifies whether to run the compiler in a child process. The default is {@code false}.
     */
    boolean fork = false

    /**
     * The options for running the compiler in a child process.
     */
    @Nested
    ForkOptions forkOptions = new ForkOptions()

    /**
     * Specifies whether to use the Ant {@code <depend>} task.
     */
    boolean useDepend = false

    /**
     * The options for using the Ant {@code <depend>} task.
     */
    DependOptions dependOptions = new DependOptions()

    /**
     * The compiler to use.
     */
    @Input @Optional
    String compiler = null
    @Input
    boolean includeJavaRuntime = false

    /**
     * The bootstrap classpath to use when compiling.
     */
    @Input @Optional
    String bootClasspath = null

    /**
     * The extension dirs to use when compiling.
     */
    @Input @Optional
    String extensionDirs = null

    /**
     * The arguments to pass to the compiler.
     */
    @Input
    List compilerArgs = []

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

    /**
     * Set the dependency options from a map.  See  {@link DependOptions}  for
     * a list of valid properties.  Calling this method will enable use
     * of the depend task during a compile.
     */
    CompileOptions depend(Map dependArgs) {
        useDepend = true
        dependOptions.define(dependArgs)
        this
    }

    List excludedFieldsFromOptionMap() {
        ['debugOptions', 'forkOptions', 'compilerArgs', 'dependOptions', 'useDepend']
    }

    Map fieldName2AntMap() {
        [
                warnings: 'nowarn',
                bootClasspath: 'bootclasspath',
                extensionDirs: 'extdirs',
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

