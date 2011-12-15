/*
 * Copyright 2007-2008 the original author or authors.
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

/**
 * Compilation options to be passed to the Groovy compiler.
 *
 * @author Hans Dockter
 */
public class GroovyCompileOptions extends AbstractOptions {
    /**
     * Tells whether the compilation task should fail if compile errors occurred. Defaults to <tt>true</tt>.
     */
    boolean failOnError = true

    /**
     * Tells whether to turn on verbose output. Defaults to <tt>false</tt>.
     */
    boolean verbose = false

    /**
     * Tells whether to print which source files are to be compiled. Defaults to <tt>false</tt>.
     */
    boolean listFiles = false

    /**
     * The source encoding. Defaults to <tt>UTF-8</tt>.
     */
    @Input
    String encoding = 'UTF-8'

    /**
     * Tells whether to run the Groovy compiler in a separate process. Defaults to <tt>true</tt>.
     */
    boolean fork = true

    /**
     * Options for running the Groovy compiler in a separate process. These options only take effect
     * if <tt>fork</tt> is set to <tt>true</tt>.
     */
    GroovyForkOptions forkOptions = new GroovyForkOptions()

    /**
     * Tells whether the Java runtime should be put on the compiler's compile class path. Defaults to <tt>false</tt>.
     */
    @Input
    boolean includeJavaRuntime = false

    /**
     * Tells whether to print a stack trace when the compiler hits a problem (like a compile error).
     * Defaults to <tt>false</tt>.
     */
    boolean stacktrace = false

    /**
     * Shortcut for setting both <tt>fork</tt> and <tt>forkOptions</tt>.
     *
     * @param forkArgs fork options in map notation
     */
    GroovyCompileOptions fork(Map forkArgs) {
        fork = true
        forkOptions.define(forkArgs)
        this
    }

    List excludedFieldsFromOptionMap() {
        ['forkOptions']
    }

    Map fieldName2AntMap() {
        [
                failOnError: 'failonerror',
                listFiles: 'listfiles'
        ]
    }

    Map optionMap() {
        super.optionMap() + forkOptions.optionMap()
    }
}
