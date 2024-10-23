/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.jvm.extension

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.*

/**
 * An extension intended for configuring the manner in which a project is
 * compiled and tested.
 */
abstract class UnitTestAndCompileExtension {

    /**
     * Set this flag to true if the project compiles against JDK internal classes.
     *
     * This workaround should be used sparingly.
     */
    abstract val usesJdkInternals: Property<Boolean>

    /**
     * Set this flag to true if the project compiles against Java standard library APIs
     * that were introduced after the [targetVersion] of the project.
     *
     * This workaround should be used sparingly.
     */
    abstract val usesFutureStdlib: Property<Boolean>

    /**
     * Set this flag to true if the project compiles against dependencies that target a
     * higher JVM version than the [targetVersion] of the project.
     *
     * This workaround should be used sparingly.
     */
    abstract val usesIncompatibleDependencies: Property<Boolean>

    /**
     * Stores the mutable value of the target bytecode version for this project,
     * but is protected to prevent the user from setting it directly.
     */
    protected abstract val targetVersionProperty: Property<Int>

    /**
     * Get the target bytecode version for this project.
     *
     * To configure this value, call a `usedIn*` method.
     */
    val targetVersion: Provider<Int>
        get() = targetVersionProperty

    /**
     * Enforces **Java 6** compatibility.
     */
    fun usedInWorkers() {
        targetVersionProperty = 6
    }

    /**
     * Enforces **Java 6** compatibility.
     */
    fun usedForStartup() {
        targetVersionProperty = 6
    }

    /**
     * Enforces **Java 7** compatibility.
     */
    fun usedInToolingApi() {
        targetVersionProperty = 7
    }

    /**
     * Enforces **Java 8** compatibility.
     */
    fun usedInDaemon() {
        targetVersionProperty = 8
    }

}
