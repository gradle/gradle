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

package org.gradle.kotlin.dsl.execution


/**
 * A residual Kotlin DSL program can be:
 * - empty
 * - a single buildscript block
 * - a single plugins block
 * - a buildscript block followed by a plugins block
 * - a script with neither a buildscript nor a plugins block
 * - a script preceded by a buildscript or plugins block or both
 */
sealed class Program {

    object Empty : Program() {

        override fun toString() = "Empty"
    }

    data class Buildscript(val fragment: ProgramSourceFragment) : Stage1() {

        override val fragments: List<ProgramSourceFragment>
            get() = listOf(fragment)
    }

    data class Plugins(val fragment: ProgramSourceFragment) : Stage1() {

        override val fragments: List<ProgramSourceFragment>
            get() = listOf(fragment)
    }

    data class Stage1Sequence(val buildscript: Buildscript, val plugins: Plugins) : Stage1() {

        override val fragments: List<ProgramSourceFragment>
            get() = listOf(buildscript.fragment, plugins.fragment)
    }

    data class Script(val source: ProgramSource) : Program()

    data class Staged(val stage1: Stage1, val stage2: Script) : Program()

    abstract class Stage1 : Program() {

        abstract val fragments: List<ProgramSourceFragment>
    }
}
