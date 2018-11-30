/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language

import org.gradle.nativeplatform.fixtures.AvailableToolChains

abstract trait LanguageTaskNames {
    private static final String DEBUG = 'Debug'
    private static final String RELEASE = 'Release'

    abstract AvailableToolChains.InstalledToolChain getToolchainUnderTest()

    abstract String getLanguageTaskSuffix()

    String installTaskDebug(String project = '') {
        installTask(project, DEBUG)
    }

    String[] compileTasks(String project = '', String buildType) {
        [compileTask(project, buildType)] as String[]
    }

    String compileTask(String project = '', String buildType) {
        "${project}:compile${buildType}${getLanguageTaskSuffix()}"
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
        return new ProjectTasks(project, toolchainUnderTest, languageTaskSuffix)
    }

    /**
     * Returns the tasks for the root project.
     */
    ProjectTasks getTasks() {
        return new ProjectTasks('', toolchainUnderTest, languageTaskSuffix)
    }

    static class ProjectTasks {
        private final String project
        private final AvailableToolChains.InstalledToolChain toolChainUnderTest
        private final String languageTaskSuffix
        private String architecture = null
        private String operatingSystemFamily = null

        ProjectTasks(String project, AvailableToolChains.InstalledToolChain toolChainUnderTest, String languageTaskSuffix) {
            this.toolChainUnderTest = toolChainUnderTest
            this.project = project
            this.languageTaskSuffix = languageTaskSuffix
        }

        ProjectTasks withArchitecture(String architecture) {
            this.architecture = architecture
            return this
        }

        DebugTasks getDebug() {
            return new DebugTasks()
        }

        ReleaseTasks getRelease() {
            return new ReleaseTasks()
        }

        TestTasks getTest() {
            return new TestTasks()
        }

        private withProject(String t) {
            project + ":" + t
        }

        ProjectTasks withOperatingSystemFamily(String operatingSystemFamily) {
            this.operatingSystemFamily = operatingSystemFamily
            return this
        }

        class AbstractTasks {
            final String type

            AbstractTasks(String type) {
                this.type = type.capitalize()
            }

            String getCompile() {
                return withProject("compile${type}${variant}${languageTaskSuffix}")
            }

            String getLink() {
                return withProject("link${type}${variant}")
            }

            String getInstall() {
                return withProject("install${type}${variant}")
            }

            String getAssemble() {
                return withProject("assemble${type}${variant}")
            }

        }

        class DebugTasks extends AbstractTasks {
            DebugTasks() {
                super("debug")
            }

            List<String> getAllToLink() {
                return [compile, link]
            }

            List<String> getAllToInstall() {
                return allToLink + [install]
            }
        }

        class ReleaseTasks extends AbstractTasks {
            ReleaseTasks() {
                super("release")
            }

            List<String> getExtract() {
                if (toolChainUnderTest.visualCpp) {
                    return []
                } else {
                    return [withProject("extractSymbolsRelease${variant}")]
                }
            }

            List<String> getStrip() {
                if (toolChainUnderTest.visualCpp) {
                    return []
                } else {
                    return [withProject("stripSymbolsRelease${variant}")]
                }
            }

            List<String> getAllToLink() {
                return [compile, link] + strip
            }

            List<String> getAllToInstall() {
                return allToLink + [install]
            }
        }

        class TestTasks extends AbstractTasks {
            TestTasks() {
                super("test")
            }

            List<String> getRun() {
                return [withProject("runTest${variant}")]
            }

            List<String> getRelocate() {
                return [withProject("relocateMainForTest${variant}")]
            }

            List<String> getAllToLink() {
                return [compile, link]
            }

            List<String> getAllToInstall() {
                return allToLink + [install]
            }
        }

        protected String getVariant() {
            String result = ""
            if (operatingSystemFamily != null) {
                result += operatingSystemFamily.toLowerCase().capitalize()
            }
            if (architecture != null) {
                result += architecture.toLowerCase().capitalize()
            }
            return result
        }
    }
}