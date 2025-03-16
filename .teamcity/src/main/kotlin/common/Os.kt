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

enum class Arch(
    val suffix: String,
    val nameOnLinuxWindows: String,
    val nameOnMac: String,
) {
    AMD64("64bit", "amd64", "x86_64"),
    AARCH64("aarch64", "aarch64", "aarch64"),
    ;

    fun asName() = name.lowercase().toCapitalized()
}

enum class Os(
    val agentRequirement: String,
    val androidHome: String,
    val jprofilerHome: String,
    val perfTestWorkingDir: String = "%teamcity.build.checkoutDir%",
    val perfTestJavaVendor: JvmVendor = JvmVendor.OPENJDK,
    val buildJavaVersion: JvmVersion = BuildToolBuildJvm.version,
    val perfTestJavaVersion: JvmVersion = JvmVersion.JAVA_17,
    val defaultArch: Arch = Arch.AMD64,
) {
    LINUX(
        "Linux",
        androidHome = "/opt/android/sdk",
        jprofilerHome = "/opt/jprofiler/jprofiler11.1.4",
    ),
    ALPINE(
        "Linux",
        androidHome = "/not/supported",
        jprofilerHome = "/not/supported",
    ),
    WINDOWS(
        "Windows",
        androidHome = """C:\Program Files\android\sdk""",
        jprofilerHome = """C:\Program Files\jprofiler\jprofiler11.1.4""",
        perfTestWorkingDir = "P:/",
    ),
    MACOS(
        "Mac",
        androidHome = "/opt/android/sdk",
        jprofilerHome = "/Applications/JProfiler11.1.4.app",
        defaultArch = Arch.AARCH64,
    ),
    ;

    fun escapeKeyValuePair(
        key: String,
        value: String,
    ) = if (this == WINDOWS) """$key="$value"""" else """"$key=$value""""

    fun asName() = name.lowercase().toCapitalized()

    fun javaInstallationLocations(arch: Arch = Arch.AMD64): String {
        val paths =
            when {
                this == LINUX ->
                    listOf(
                        DefaultJvm(JvmVersion.JAVA_7, JvmVendor.ORACLE),
                        DefaultJvm(JvmVersion.JAVA_8, JvmVendor.ORACLE),
                        DefaultJvm(JvmVersion.JAVA_11, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_17, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_21, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_23, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_24, JvmVendor.OPENJDK),
                    )

                arch == Arch.AARCH64 && this == MACOS ->
                    listOf(
                        DefaultJvm(JvmVersion.JAVA_8, JvmVendor.ZULU),
                        DefaultJvm(JvmVersion.JAVA_11, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_17, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_21, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_23, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_24, JvmVendor.OPENJDK),
                    )

                else ->
                    listOf(
                        DefaultJvm(JvmVersion.JAVA_8, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_11, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_17, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_21, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_23, JvmVendor.OPENJDK),
                        DefaultJvm(JvmVersion.JAVA_24, JvmVendor.OPENJDK),
                    )
            }.joinToString(",") { javaHome(it, this, arch) }
        return """"-Porg.gradle.java.installations.paths=$paths""""
    }
}
