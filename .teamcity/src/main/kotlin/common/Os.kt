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
ps aux | egrep 'Gradle(Daemon|Worker)' | awk '{print ${'$'}2}'

COUNTER=0

while [ ${'$'}COUNTER -lt 30 ]
do
    if [ `ps aux | egrep 'Gradle(Daemon|Worker)' | wc -l` -eq "0" ]; then
       echo "All daemons are killed, exit."
       free -m
       exit 0
    fi

    echo "Attempt ${'$'}COUNTER: failed to kill daemons."
    ps aux | egrep 'Gradle(Daemon|Worker)' | awk '{print ${'$'}2}' | xargs kill
    sleep 1

    COUNTER=`expr ${'$'}COUNTER + 1`
done

echo "Waiting timeout for daemons to exit, kill -9 now."

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

enum class Arch(val suffix: String, val nameOnLinuxWindows: String, val nameOnMac: String) {
    AMD64("64bit", "amd64", "x86_64"),
    AARCH64("aarch64", "aarch64", "aarch64");

    fun asName() = name.lowercase().toCapitalized()
}

enum class Os(
    val agentRequirement: String,
    val androidHome: String,
    val jprofilerHome: String,
    val killAllGradleProcesses: String,
    val perfTestWorkingDir: String = "%teamcity.build.checkoutDir%",
    val perfTestJavaVendor: JvmVendor = JvmVendor.openjdk,
    val buildJavaVersion: JvmVersion = JvmVersion.java11,
    val perfTestJavaVersion: JvmVersion = JvmVersion.java17,
    val defaultArch: Arch = Arch.AMD64
) {
    LINUX(
        "Linux",
        androidHome = "/opt/android/sdk",
        jprofilerHome = "/opt/jprofiler/jprofiler11.1.4",
        killAllGradleProcesses = killAllGradleProcessesUnixLike
    ),
    WINDOWS(
        "Windows",
        androidHome = """C:\Program Files\android\sdk""",
        jprofilerHome = """C:\Program Files\jprofiler\jprofiler11.1.4""",
        killAllGradleProcesses = killAllGradleProcessesWindows,
        perfTestWorkingDir = "P:/",
    ),
    MACOS(
        "Mac",
        androidHome = "/opt/android/sdk",
        jprofilerHome = "/Applications/JProfiler11.1.4.app",
        killAllGradleProcesses = killAllGradleProcessesUnixLike,
        defaultArch = Arch.AARCH64
    );

    fun escapeKeyValuePair(key: String, value: String) = if (this == WINDOWS) """$key="$value"""" else """"$key=$value""""

    fun asName() = name.lowercase().toCapitalized()

    fun javaInstallationLocations(): String {
        val paths = enumValues<JvmVersion>().joinToString(",") { version ->
            val vendor = when {
                version.major >= 11 -> JvmVendor.openjdk
                else -> JvmVendor.oracle
            }
            javaHome(DefaultJvm(version, vendor), this)
        } + ",${javaHome(DefaultJvm(JvmVersion.java8, JvmVendor.openjdk), this)}"
        return """"-Porg.gradle.java.installations.paths=$paths""""
    }
}
