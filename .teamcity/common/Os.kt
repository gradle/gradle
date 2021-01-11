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

private const val killAllGradleProcessesUnixLike = """
free -m
ps aux | egrep 'Gradle(Daemon|Worker)'
ps aux | egrep 'Gradle(Daemon|Worker)' | awk '{print ${'$'}2}' | xargs kill -9
free -m
ps aux | egrep 'Gradle(Daemon|Worker)' | awk '{print ${'$'}2}'
"""

private const val killAllGradleProcessesWindows = """
wmic OS get FreePhysicalMemory,FreeVirtualMemory,FreeSpaceInPagingFiles /VALUE
wmic Path win32_process Where "name='java.exe'"
wmic Path win32_process Where "name='java.exe' AND CommandLine Like '%%%%GradleDaemon%%%%'" Call Terminate
wmic Path win32_process Where "name='java.exe' AND CommandLine Like '%%%%GradleWorker%%%%'" Call Terminate
wmic OS get FreePhysicalMemory,FreeVirtualMemory,FreeSpaceInPagingFiles /VALUE
wmic Path win32_process Where "name='java.exe'"
"""

enum class Os(
    val agentRequirement: String,
    val ignoredSubprojects: List<String> = emptyList(),
    val androidHome: String,
    val jprofilerHome: String,
    val killAllGradleProcesses: String,
    val perfTestWorkingDir: String = "%teamcity.build.checkoutDir%",
    val perfTestJavaVendor: String = "oracle",
    val buildJavaVersion: JvmVersion = JvmVersion.java11,
    val perfTestJavaVersion: JvmVersion = JvmVersion.java8
) {
    LINUX("Linux",
        androidHome = "/opt/android/sdk",
        jprofilerHome = "/opt/jprofiler/jprofiler11.1.4",
        killAllGradleProcesses = killAllGradleProcessesUnixLike
    ),
    WINDOWS("Windows",
        androidHome = """C:\Program Files\android\sdk""",
        jprofilerHome = """C:\Program Files\jprofiler\jprofiler11.1.4""",
        killAllGradleProcesses = killAllGradleProcessesWindows,
        perfTestWorkingDir = "P:/"),
    MACOS("Mac",
        listOf("integ-test", "native", "plugins", "resources", "scala", "workers", "wrapper", "tooling-native"),
        androidHome = "/opt/android/sdk",
        jprofilerHome = "/Applications/JProfiler11.1.4.app",
        killAllGradleProcesses = killAllGradleProcessesUnixLike
    );

    fun escapeKeyValuePair(key: String, value: String) = if (this == WINDOWS) """$key="$value"""" else """"$key=$value""""

    fun asName() = name.toLowerCase().capitalize()

    fun javaInstallationLocations(): String {
        return escapeKeyValuePair("-Porg.gradle.java.installations.paths", enumValues<JvmVersion>().map { version -> {
            val vendor = if (version.major >= 11) JvmVendor.openjdk else JvmVendor.oracle
            asPlaceholder(version, vendor)
        }() }.joinToString(","))
    }

    fun javaHomeForGradle(): String {
        return asPlaceholder(JvmVersion.java11, JvmVendor.openjdk)
    }

    fun asPlaceholder(jvmVersion: JvmVersion, vendor: JvmVendor): String {
        return "%${name.toLowerCase()}.$jvmVersion.$vendor.64bit%"
    }
}
