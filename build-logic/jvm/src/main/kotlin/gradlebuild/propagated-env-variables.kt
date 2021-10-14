/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild

import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import java.util.concurrent.Callable
import javax.inject.Inject


val propagatedEnvironmentVariables = listOf(
    // Obviously necessary
    "HOME",
    "SHELL",
    "TERM",

    // Otherwise Windows complains "Unrecognized Windows Sockets error: 10106"
    "SystemRoot",
    "OS",

    // For Android tests
    "ANDROID_HOME",
    "ANDROID_SDK_ROOT",
    // legacy and new android user home
    "ANDROID_USER_HOME",
    "ANDROID_PREFS_ROOT",

    // Used by Visual Studio
    "USERNAME",
    "USER",
    "USERDOMAIN",
    "USERPROFILE",
    "LOCALAPPDATA",

    // Used by Gradle test infrastructure
    "REPO_MIRROR_URLS",

    // Used to find local java installations
    "SDKMAN_CANDIDATES_DIR",

    // temp dir
    "TMPDIR",
    "TMP",
    "TEMP",

    // Seems important on Windows
    "ALLUSERSPROFILE",
    "PUBLIC",
    "windir",

    // Used by performance test to recognize TeamCity buildId
    "BUILD_ID",
    "JPROFILER_HOME",

    //
    "LANG",
    "LANGUAGE",
    "LC_ALL",
    "LC_CTYPE",

    "JDK_11",
    "JDK_11_0",
    "JDK_11_0_x64",
    "JDK_11_x64",
    "JDK_14_0",
    "JDK_14_0_x64",
    "JDK_15_0",
    "JDK_15_0_x64",
    "JDK_16",
    "JDK_16_0",
    "JDK_16_0_x64",
    "JDK_16_x64",
    "JDK_17",
    "JDK_17_0",
    "JDK_17_0_x64",
    "JDK_17_x64",
    "JDK_18",
    "JDK_18_x64",
    "JDK_19",
    "JDK_19_x64",
    "JDK_1_6",
    "JDK_1_6_x64",
    "JDK_1_7",
    "JDK_1_7_x64",
    "JDK_1_8",
    "JDK_1_8_x64",
    "JDK_9",
    "JDK_9_0",
    "JDK_9_0_x64",
    "JDK_9_x64",
    "JDK_HOME",
    "JRE_HOME",
    "CommonProgramFiles",
    "CommonProgramFiles(x86)",
    "CommonProgramW6432",
    "ProgramData",
    "ProgramFiles",
    "ProgramFiles(x86)",
    // Simply putting PATH there isn't enough. Windows has case-insensitive env vars but something else fails if the Path variable is published as PATH for test tasks.
    OperatingSystem.current().pathVar,
    "PATHEXT",
)


val credentialsKeywords = listOf(
    "api_key",
    "access_key",
    "apikey",
    "accesskey",
    "password",
    "token",
    "credential",
    "auth"
)


// This build service serves as a global cache for propagated environment variables. Each potentially propagated variable requires a provider to be created. These aren't very expensive but still have
// non-trivial cost, and they are recorded in the configuration cache. It makes sense to reduce the number of providers to a minimum to reduce memory pressure and speed up configuration cache
// reads/writes. Naive approach of initializing a new map for each test task yield ~560K providers (~70 vars per ~8000 tasks) and even per-project cache still produces ~8000 (~70 vars per 113
// projects) which is noticeable. Numbers are actual at the time of writing (Oct 2021).
@Suppress("UnstableApiUsage")
private
abstract class PropagatedVariablesCacheBuildService @Inject constructor(private val providers: ProviderFactory) : BuildService<BuildServiceParameters.None>, Callable<Map<String, String?>> {
    private
    val envVars: Map<String, String?> by lazy {
        val result = HashMap<String, String>()
        propagatedEnvironmentVariables.forEach { varName ->
            val value = providers.environmentVariable(varName).forUseAtConfigurationTime().orNull
            if (value != null) {
                result[varName] = value
            }
        }
        result.forEach { (key, _) ->
            if (credentialsKeywords.any { key.contains(it, true) }) {
                throw IllegalArgumentException("Found sensitive data in filtered environment variables: $key")
            }
        }
        result
    }

    // Access through the Callable interface is used to work around different classloaders loading the real class in different projects. Ensuring that the PropagatedVariablesCacheBuildService type
    // gets registered  in a common classloader is a major hassle. Instead, the first project to request filtering registers the instance loaded with its classloader. All other projects just cast the
    // returned instance to Callable because their PropagatedVariablesCacheBuildService classes are loaded with their classloader and therefore incompatible.
    override fun call() = envVars
}


@Suppress("UnstableApiUsage")
fun Test.filterEnvironmentVariables() {
    // Explicit Callable type is important here to avoid cast exception if the shared service has been already registered - but with other classloader.
    val variableService: Callable<Map<String, String?>> = project.gradle.sharedServices.registerIfAbsent(
        "gradlebuild.propagated-env-variables.FilteredVariablesBuildService",
        PropagatedVariablesCacheBuildService::class
    ) {}.get()
    environment = variableService.call()
}
