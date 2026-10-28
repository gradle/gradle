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
 * A source Kotlin DSL program can be:
 * - empty
 * - a single buildscript block
 * - a single plugins block
 * - a buildscript block followed by a plugins block
 * - a script with neither a buildscript nor a plugins block
 * - a script preceded by a buildscript or plugins block or both
 *
 * The evaluation of a Kotlin DSL program happens, in the general case, in
 * [two stages](https://en.wikipedia.org/wiki/Multi-stage_programming):
 * - in stage 1, the [Buildscript] and [Plugins] programs are executed and their execution
 *   is assumed to affect the classpath available to the stage 2 program;
 * - in stage 2, the remaining [Script] must be evaluated against the dynamically resolved classpath and,
 *   for that reason, [stage 2 programs][Script] can only be specialized after stage 1 executes at least once;
 */
sealed class Program {

    /**
     * A program with no observable side-effects.
     */
    object Empty : Program() {

        override fun toString() = "Empty"
    }

    /**
     * A `buildscript` / `initscript` block.
     */
    data class Buildscript(override val fragment: ProgramSourceFragment) : Stage1(), FragmentHolder

    /**
     * A `pluginManagement` block.
     */
    data class PluginManagement(override val fragment: ProgramSourceFragment) : Stage1(), FragmentHolder

    /**
     * A `plugins` block.
     */
    data class Plugins(override val fragment: ProgramSourceFragment) : Stage1(), FragmentHolder

    interface FragmentHolder {
        val fragment: ProgramSourceFragment
    }

    /**
     * An optional `pluginManagement` block followed by a `buildscript` block then followed by a `plugins` block.
     */
    data class Stage1Sequence(val pluginManagement: PluginManagement?, val buildscript: Buildscript?, val plugins: Plugins?) : Stage1()

    /**
     * A script that must be dynamically evaluated after stage 1 completes and the script classpath
     * becomes available.
     */
    data class Script(val source: ProgramSource) : Program()

    /**
     * A [Stage1] program followed by a stage 2 [Script] program.
     */
    data class Staged(val stage1: Stage1, val stage2: Script) : Program()

    /**
     * Any stage 1 program, one of [Buildscript], [Plugins] or [a sequence of the two][Stage1Sequence].
     */
    abstract class Stage1 : Program()
}
