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

import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem


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
    "YARNPKG_MIRROR_URL",

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
    // Used by some tests to be ignored in specific build
    "BUILD_TYPE_ID",
    "JPROFILER_HOME",

    // Used by mirror init script, see RepoScriptBlockUtil
    "IGNORE_MIRROR",

    "LANG",
    "LANGUAGE",
    // It is possible to have many LC_xxx variables for different aspects of the locale. However, LC_ALL overrides all of them, and it is what CI uses.
    "LC_ALL",
    "LC_CTYPE",

    "JDK8",
    "JDK11",
    "JDK17",

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
    // Used by KotlinMultiplatformPluginSmokeTest, see https://github.com/gradle/gradle-private/issues/4223
    "CHROME_BIN"
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


fun Test.filterEnvironmentVariables() {
    environment = makePropagatedEnvironment()
    environment.forEach { (key, _) ->
        require(credentialsKeywords.none { key.contains(it, true) }) { "Found sensitive data in filtered environment variables: $key" }
    }
}


private
fun makePropagatedEnvironment(): Map<String, String?> {
    val result = HashMap<String, String?>()
    for (key in propagatedEnvironmentVariables) {
        val value = System.getenv(key)
        if (value != null) {
            result[key] = value
        }
    }
    return result
}
