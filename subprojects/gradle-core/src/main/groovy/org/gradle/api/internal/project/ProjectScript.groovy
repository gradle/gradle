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

package org.gradle.api.internal.project

import org.gradle.groovy.scripts.BasicScript
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

abstract class ProjectScript extends BasicScript {

    def void apply(Closure closure) {
        scriptTarget.apply(closure)
    }

    def void apply(Map options) {
        scriptTarget.apply(options)
    }

    def ScriptHandler getBuildscript() {
        scriptTarget.buildscript
    }

    def void buildscript(Closure configureClosure) {
        scriptTarget.buildscript(configureClosure)
    }

    def File file(Object path) {
        scriptTarget.file(path)
    }

    def ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        scriptTarget.files(paths, configureClosure)
    }

    def ConfigurableFileCollection files(Object ... paths) {
        scriptTarget.files(paths)
    }

    def void disableStandardOutputCapture() {
        scriptTarget.disableStandardOutputCapture()
    }

    def void captureStandardOutput(LogLevel level) {
        scriptTarget.captureStandardOutput(level)
    }

    def Logger getLogger() {
        scriptTarget.logger
    }

    def String toString() {
        scriptTarget.toString()
    }
}
