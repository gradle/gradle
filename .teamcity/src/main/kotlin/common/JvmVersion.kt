/*
 * Copyright 2019 the original author or authors.
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

package common

enum class JvmVersion(
    val major: Int,
) {
    JAVA_7(7),
    JAVA_8(8),
    JAVA_11(11),
    JAVA_17(17),
    JAVA_21(21),
    JAVA_23(23),
    JAVA_24(24),
    ;

    fun toCapitalized(): String = name.replace("_", "").lowercase().toCapitalized()
}
