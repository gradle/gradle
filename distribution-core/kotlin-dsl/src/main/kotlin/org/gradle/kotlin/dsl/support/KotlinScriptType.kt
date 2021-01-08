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

package org.gradle.kotlin.dsl.support

import java.io.File


fun kotlinScriptTypeFor(candidate: File): KotlinScriptType? =
    KotlinScriptTypeMatch.forFile(candidate)?.scriptType


enum class KotlinScriptType {
    INIT, SETTINGS, PROJECT
}


data class KotlinScriptTypeMatch(
    val scriptType: KotlinScriptType,
    val match: Match
) {

    companion object {

        fun forFile(file: File): KotlinScriptTypeMatch? =
            forName(file.name)

        fun forName(name: String): KotlinScriptTypeMatch? =
            candidates.firstOrNull { it.match.matches(name) }

        private
        val candidates =
            listOf(
                KotlinScriptTypeMatch(KotlinScriptType.SETTINGS, Match.Whole("settings.gradle.kts")),
                KotlinScriptTypeMatch(KotlinScriptType.SETTINGS, Match.Suffix(".settings.gradle.kts")),
                KotlinScriptTypeMatch(KotlinScriptType.INIT, Match.Whole("init.gradle.kts")),
                KotlinScriptTypeMatch(KotlinScriptType.INIT, Match.Suffix(".init.gradle.kts")),
                KotlinScriptTypeMatch(KotlinScriptType.PROJECT, Match.Suffix(".gradle.kts"))
            )
    }
}


sealed class Match {

    abstract val value: String

    abstract fun matches(candidate: String): Boolean

    data class Whole(override val value: String) : Match() {
        override fun matches(candidate: String) = candidate == value
    }

    data class Suffix(override val value: String) : Match() {
        override fun matches(candidate: String) = candidate.endsWith(value)
    }
}
