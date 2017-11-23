/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing

import groovy.transform.CompileStatic

import java.util.regex.Pattern

@CompileStatic
class LeakingProcessKillPattern {
    private LeakingProcessKillPattern() {}

    static String generate(String agentDir, String rootProjectDir, String rootBuildDir) {
        return "(?i)[/\\\\](java(?:\\.exe)?.+?(?:${toOrgGradleProcessInProjectDirectoryPattern(rootProjectDir)}|${toOrgGradleProcessInBuildDirectoryPattern(rootBuildDir)}|${toNettyServerPattern(rootProjectDir)}|${toWorkerProcessPattern(agentDir)}).+)"
    }

    private static String toNettyServerPattern(String rootProjectDir) {
        return "(?:-classpath.+${Pattern.quote(rootProjectDir)}.+?(play\\.core\\.server\\.NettyServer))"
    }

    private static String toOrgGradleProcessInBuildDirectoryPattern(String rootBuildDir) {
        return "(?:-classpath.+${Pattern.quote(rootBuildDir)}.+?(org\\.gradle\\.|[a-zA-Z]+))"
    }

    private static String toOrgGradleProcessInProjectDirectoryPattern(String rootProjectDir) {
        return "(?:-cp.+${Pattern.quote(rootProjectDir)}.+?(org\\.gradle\\.|[a-zA-Z]+))"
    }

    private static String toWorkerProcessPattern(String agentDir) {
        return "(?:-cp.+${Pattern.quote(agentDir)}[\\\\/]\\.gradle.+?(worker\\.org\\.gradle\\.process\\.internal\\.worker\\.GradleWorkerMain))"
    }
}
