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

/**
 * An extension intended for configuring the manner in which a project is
 * compiled and tested.
 */
abstract class UnitTestAndCompileExtension {

    companion object {
        const val NAME: String = "jvmCompile"
    }

    /**
     * Set this flag to true if the project compiles against JDK internal classes.
     *
     * This workaround should be used sparingly.
     */
    abstract val usesJdkInternals: Property<Boolean>

    /**
     * Set this flag to true if the project compiles against Java standard library APIs
     * that were introduced after the target JVM bytecode version of the project.
     *
     * This workaround should be used sparingly.
     */
    abstract val usesFutureStdlib: Property<Boolean>

    /**
     * The JVM version that all JVM code in this module will target.
     */
    abstract val targetJvmVersion: Property<Int>

}
