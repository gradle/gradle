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

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.AvailableToolChains.InstalledToolChain

abstract trait CppTaskNames {

    private static final String DEBUG = 'Debug'
    private static final String RELEASE = 'Release'

    abstract InstalledToolChain getToolchainUnderTest()

    String installTaskDebug(String project = '') {
        installTask(project, DEBUG)
    }

    String[] compileTasks(String project = '', String buildType) {
        [compileTask(project, buildType)] as String[]
    }

    String compileTask(String project = '', String buildType) {
        "${project}:compile${buildType}Cpp"
    }

    String linkTask(String project = '', String buildType) {
        "${project}:link${buildType}"
    }

    String staticLinkTask(String project = '', String buildType) {
        "${project}:create${buildType}"
    }

    String installTask(String project = '', String buildType) {
        "${project}:install${buildType}"
    }

    String[] compileAndLinkTasks(List<String> projects = [''], String buildType) {
        projects.collect { project ->
            [*compileTasks(project, buildType), linkTask(project, buildType)]
        }.flatten()
    }

    String[] compileAndStaticLinkTasks(List<String> projects = [''], String buildType) {
        projects.collect { project ->
            [*compileTasks(project, buildType), staticLinkTask(project, buildType)]
        }.flatten()
    }

    String[] extractAndStripSymbolsTasksRelease(String project = '') {
        return extractAndStripSymbolsTasks(project, RELEASE)
    }

    String[] extractAndStripSymbolsTasks(String project = '', String buildType) {
        if (toolchainUnderTest.visualCpp) {
            return []
        } else {
            return stripSymbolsTasks(project, buildType) + ["${project}:extractSymbols${buildType}"]
        }
    }

    String[] stripSymbolsTasksRelease(String project = '') {
        return stripSymbolsTasks(project, RELEASE)
    }

    String[] stripSymbolsTasks(String project = '', String buildType) {
        if (toolchainUnderTest.visualCpp) {
            return []
        } else {
            return ["${project}:stripSymbols${buildType}"]
        }
    }

    String[] stripSymbolsTasks(List<String> projects, String buildType) {
        projects.collect { project ->
            [*stripSymbolsTasks(project, buildType)]
        }.flatten()
    }

    String getDebug() {
        DEBUG
    }

    String getRelease() {
        RELEASE
    }

    String getDebugShared() {
        return "${DEBUG}Shared"
    }

    /**
     * Returns the tasks for the project with the given path.
     */
    ProjectTasks tasks(String project) {
        return new ProjectTasks(project, toolchainUnderTest)
    }

    /**
     * Returns the tasks for the root project.
     */
    ProjectTasks getTasks() {
        return new ProjectTasks('', toolchainUnderTest)
    }

    static class ProjectTasks {
        private final String project
        private final InstalledToolChain toolChainUnderTest

        ProjectTasks(String project, InstalledToolChain toolChainUnderTest) {
            this.toolChainUnderTest = toolChainUnderTest
            this.project = project
        }

        DebugTasks getDebug() {
            return new DebugTasks()
        }

        ReleaseTasks getRelease() {
            return new ReleaseTasks()
        }

        private withProject(String t) {
            project + ":" + t
        }

        class DebugTasks {
            String getCompile() {
                return withProject("compileDebugCpp")
            }

            String getLink() {
                return withProject("linkDebug")
            }

            String getInstall() {
                return withProject("installDebug")
            }

            String getAssemble() {
                return withProject("assembleDebug")
            }

            List<String> getAllToLink() {
                return [compile, link]
            }

            List<String> getAllToInstall() {
                return allToLink + [install]
            }
        }

        class ReleaseTasks {
            String getCompile() {
                return withProject("compileReleaseCpp")
            }

            String getLink() {
                return withProject("linkRelease")
            }

            String getInstall() {
                return withProject("installRelease")
            }

            String getAssemble() {
                return withProject("assembleRelease")
            }

            List<String> getExtract() {
                if (toolChainUnderTest.visualCpp) {
                    return []
                } else {
                    return [withProject("extractSymbolsRelease")]
                }
            }

            List<String> getStrip() {
                if (toolChainUnderTest.visualCpp) {
                    return []
                } else {
                    return [withProject("stripSymbolsRelease")]
                }
            }

            List<String> getAllToLink() {
                return [compile, link] + strip
            }

            List<String> getAllToInstall() {
                return allToLink + [install]
            }
        }
    }

}
