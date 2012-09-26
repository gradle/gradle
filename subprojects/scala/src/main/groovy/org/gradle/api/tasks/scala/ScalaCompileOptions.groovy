/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.gradle.api.tasks.compile.AbstractOptions
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

public class ScalaCompileOptions extends AbstractOptions {

    /**
     * Whether to use the fsc compile daemon.
     */
    boolean useCompileDaemon = false

    // NOTE: Does not work for scalac 2.7.1 due to a bug in the ant task
    /**
     * Server (host:port) on which the compile daemon is running.
     * The host must share disk access with the client process.
     * If not specified, launches the daemon on the localhost.
     * This parameter can only be specified if useCompileDaemon is true.
     */
    String daemonServer;

    /**
     * Fail the build on compilation errors.
     */
    boolean failOnError = true

    /**
     * Generate deprecation information.
     */
    boolean deprecation = true

    /**
     * Generate unchecked information.
     */
    boolean unchecked = true

    /**
     * Generate debugging information.
     * Legal values: none, source, line, vars, notailcalls
     */
    @Input @Optional
    String debugLevel

    /**
     * Run optimisations.
     */
    @Input
    boolean optimize = false

    /**
     * Encoding of source files.
     */
    @Input @Optional
    String encoding = null

    /**
     * Whether to force the compilation of all files.
     * Legal values:
     * - never (only compile modified files)
     * - changed (compile all files when at least one file is modified)
     * - always (always recompile all files)
     */
    String force = "never"

    /**
     * Specifies which backend to use.
     * Legal values: 1.4, 1.5
     */
    @Input
    String targetCompatibility = '1.5'

    /**
     * Additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     */
    List<String> additionalParameters

    /**
     * List files to be compiled.
     */
    boolean listFiles

    /**
     * Specifies the amount of logging.
     * Legal values:  none, verbose, debug
     */
    String loggingLevel

    /**
     * Phases of the compiler to log.
     * Legal values: namer, typer, pickler, uncurry, tailcalls, transmatch, explicitouter, erasure,
     *               lambdalift, flatten, constructors, mixin, icode, jvm, terminal.
     */
    List<String> loggingPhases

    /**
     * Tells whether to use incremental compilation. If {@code true}, dependency analysis will be performed
     * to only recompile code affected by source code changes that occurred after the previous compilation.
     * Incremental compilation is based on the sbt/Zinc incremental compiler.
     */
    boolean incremental

    /**
     * File location to store results of dependency analysis. Only used if {@code incremental} is {@code true}.
     */
    File incrementalCacheFile

    Map fieldName2AntMap() {
        [
                failOnError: 'failonerror',
                loggingLevel: 'logging',
                loggingPhases: 'logPhase',
                targetCompatibility: 'target',
                optimize: 'optimise',
                daemonServer: 'server',
                listFiles: 'scalacDebugging',
                debugLevel: 'debugInfo',
                additionalParameters: 'addParams'
        ]
    }

    Map fieldValue2AntMap() {
        [
                deprecation: {toOnOffString(deprecation)},
                unchecked: {toOnOffString(unchecked)},
                optimize: {toOnOffString(optimize)},
                targetCompatibility: {"jvm-${targetCompatibility}"},
                loggingPhases: {loggingPhases.isEmpty() ? ' ' : loggingPhases.join(',')},
                additionalParameters: {additionalParameters.isEmpty() ? ' ' : additionalParameters.join(' ')},
        ]
    }

    List excludedFieldsFromOptionMap() {
        ['useCompileDaemon', 'incremental', 'incrementalCacheFile'] + (optimize ? [] : ['optimize'])
    }

    private String toOnOffString(value) {
        return value ? 'on' : 'off'
    }

}
