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

package gradlebuild.incubation.action


/**
 * The kind of an incubating API declaration. Emitted by the per-project producer into the
 * intermediate txt report and consumed by the aggregator to surface a "Kind" column.
 */
enum class IncubatingApiKind {
    CLASS,
    INTERFACE,
    ENUM,
    ANNOTATION,
    METHOD,
    FIELD,
    PROPERTY,
    PACKAGE,
    OTHER;

    companion object {
        fun fromString(value: String): IncubatingApiKind =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
