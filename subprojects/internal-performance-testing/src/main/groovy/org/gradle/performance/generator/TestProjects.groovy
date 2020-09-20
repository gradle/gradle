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

package org.gradle.performance.generator

class TestProjects {
    static List<String> getProjectMemoryOptions(String testProject) {
        def daemonMemory = determineDaemonMemory(testProject)
        return ["-Xms${daemonMemory}", "-Xmx${daemonMemory}"]
    }

    private static String determineDaemonMemory(String testProject) {
        switch (testProject) {
            case 'smallCppApp':
                return '256m'
            case 'mediumCppApp':
                return '256m'
            case 'mediumCppAppWithMacroIncludes':
                return '256m'
            case 'bigCppApp':
                return '256m'
            case 'smallCppMulti':
                return '256m'
            case 'mediumCppMulti':
                return '256m'
            case 'mediumCppMultiWithMacroIncludes':
                return '256m'
            case 'bigCppMulti':
                return '1g'
            case 'nativeDependents':
                return '3g'
            case 'smallNative':
                return '256m'
            case 'mediumNative':
                return '256m'
            case 'bigNative':
                return '1g'
            case 'multiNative':
                return '256m'
            case 'withVerboseJUnit':
                return '256m'
            case 'withVerboseTestNG':
                return '256m'
            case 'mediumSwiftMulti':
                return '1G'
            case 'bigSwiftApp':
                return '1G'
            case 'nativeMonolithic':
                return '2500m'
            case 'nativeMonolithicOverlapping':
                return '2500m'
            case 'mediumNativeMonolithic':
                return '512m'
            case 'smallNativeMonolithic':
                return '512m'
            case 'manyProjectsNative':
                return '1G'
            default:
                return JavaTestProject.projectFor(testProject).daemonMemory
        }
    }
}
