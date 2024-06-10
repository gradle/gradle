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

enum class JvmCategory(
    override val vendor: JvmVendor,
    override val version: JvmVersion
) : Jvm {
    MIN_VERSION(JvmVendor.oracle, JvmVersion.java8),
    // Oracle doesn't provide zip JDK distribution for Windows anymore, we avoid using it
    MIN_VERSION_WINDOWS_MAC(JvmVendor.openjdk, JvmVersion.java8),
    MAX_LTS_VERSION(JvmVendor.openjdk, JvmVersion.java21),
    MAX_VERSION(JvmVendor.openjdk, JvmVersion.java22),
    SANTA_TRACKER_SMOKE_TEST_VERSION(JvmVendor.openjdk, JvmVersion.java17),
    EXPERIMENTAL_VERSION(JvmVendor.openjdk, JvmVersion.java22)
}
