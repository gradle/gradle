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

package gradlebuild.cleanup.services

import java.util.regex.Pattern


object LeakingProcessKillPattern {

    private
    val kotlinCompilerPattern = "(?:${Pattern.quote("-Dkotlin.environment.keepalive org.jetbrains.kotlin.daemon.KotlinCompileDaemon")})"

    @JvmStatic
    fun generate(rootProjectDir: String): String =
        """(?i)[/\\](java(?:\.exe)?.+?(?:(?:-cp.+(${Pattern.quote(rootProjectDir)}|build\\tmp\\performance-test-files).+?(org\.gradle\.|[a-zA-Z]+))|(?:-classpath.+${Pattern.quote(rootProjectDir)}.+?${Pattern.quote("\\build\\")}.+?(org\.gradle\.|[a-zA-Z]+))|(?:-classpath.+${Pattern.quote(rootProjectDir)}.+?(play\.core\.server\.NettyServer))|$kotlinCompilerPattern).+)"""
}
