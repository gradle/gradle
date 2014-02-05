/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.LoggingManager
import org.gradle.groovy.scripts.DefaultScript
import org.gradle.logging.StandardOutputCapture

abstract class ProjectScript extends DefaultScript {

    def void apply(Closure closure) {
        scriptTarget.apply(closure)
    }

    def void apply(Map options) {
        scriptTarget.apply(options)
    }

    ScriptHandler getBuildscript() {
        scriptTarget.buildscript
    }

    def void buildscript(Closure configureClosure) {
        scriptTarget.buildscript(configureClosure)
    }

    def StandardOutputCapture getStandardOutputCapture() {
        scriptTarget.standardOutputCapture
    }

    def LoggingManager getLogging() {
        scriptTarget.logging
    }

    def Logger getLogger() {
        scriptTarget.logger
    }

    def String toString() {
        scriptTarget.toString()
    }
}
