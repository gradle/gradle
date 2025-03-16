/*
 * Copyright 2021 the original author or authors.
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

interface Jvm {
    val version: JvmVersion
    val vendor: JvmVendor
}

data class DefaultJvm(
    override val version: JvmVersion,
    override val vendor: JvmVendor,
) : Jvm

object BuildToolBuildJvm : Jvm {
    override val version: JvmVersion
        get() = JvmVersion.JAVA_17
    override val vendor: JvmVendor
        get() = JvmVendor.OPENJDK
}

object OpenJdk8 : Jvm {
    override val version: JvmVersion
        get() = JvmVersion.JAVA_8
    override val vendor: JvmVendor
        get() = JvmVendor.OPENJDK
}

object OpenJdk11 : Jvm {
    override val version: JvmVersion
        get() = JvmVersion.JAVA_11
    override val vendor: JvmVendor
        get() = JvmVendor.OPENJDK
}

object OpenJdk17 : Jvm {
    override val version: JvmVersion
        get() = JvmVersion.JAVA_17
    override val vendor: JvmVendor
        get() = JvmVendor.OPENJDK
}
