/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.removal.action


/**
 * A deprecation timeline marker the scanner looks for, expressed independently of any concrete Gradle
 * version. The concrete `DeprecationMessageBuilder` method name and the human description are derived
 * from the **target major version** (the next major release), so the same report works for Gradle 10
 * today and Gradle 11 once the build moves on — see [method] and [description].
 */
enum class RemovalTimeline(val group: Group) {
    /** `willBeRemovedInGradle<target>` */
    REMOVED(Group.REMOVAL),

    /** `willBecomeAnErrorInGradle<target>` */
    ERROR_THIS_MAJOR(Group.ERROR),

    /** `willBecomeAnErrorInNextMajorGradleVersion` — version-independent, always scanned. */
    ERROR_NEXT_MAJOR(Group.ERROR),

    /** `startingWithGradle<target>` */
    STARTING_THIS_MAJOR(Group.STARTING),

    /** `startingWithGradle<target + 1>` */
    STARTING_NEXT_MAJOR(Group.STARTING);

    /** The exact `DeprecationMessageBuilder` method name for the given [targetMajor] (next major version). */
    fun method(targetMajor: Int): String = when (this) {
        REMOVED -> "willBeRemovedInGradle$targetMajor"
        ERROR_THIS_MAJOR -> "willBecomeAnErrorInGradle$targetMajor"
        ERROR_NEXT_MAJOR -> "willBecomeAnErrorInNextMajorGradleVersion"
        STARTING_THIS_MAJOR -> "startingWithGradle$targetMajor"
        STARTING_NEXT_MAJOR -> "startingWithGradle${targetMajor + 1}"
    }

    /** A human-readable explanation of what the marker announces, for the given [targetMajor]. */
    fun description(targetMajor: Int): String = when (this) {
        REMOVED -> "Scheduled to be removed in Gradle $targetMajor."
        ERROR_THIS_MAJOR -> "Will become an error in Gradle $targetMajor."
        ERROR_NEXT_MAJOR -> "Will become an error in the next major Gradle version."
        STARTING_THIS_MAJOR -> "Behaviour changes starting with Gradle $targetMajor."
        STARTING_NEXT_MAJOR -> "Behaviour changes starting with Gradle ${targetMajor + 1}."
    }

    enum class Group(val displayName: String) {
        REMOVAL("Removals"),
        ERROR("Behaviour changes to errors"),
        STARTING("Behaviour changes starting with a future version")
    }

    companion object {
        /** Marker method names → marker, for the given [targetMajor]. The set the scanner matches. */
        fun markersByMethod(targetMajor: Int): Map<String, RemovalTimeline> =
            entries.associateBy { it.method(targetMajor) }

        fun fromString(value: String): RemovalTimeline? = entries.firstOrNull { it.name == value }
    }
}
