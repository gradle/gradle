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

    String[] stripSymbolsTasks(String project = '', String buildType) {
        if (toolchainUnderTest.visualCpp) {
            return []
        } else {
            return ["${project}:stripSymbols${buildType}"]
        }
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
        return new ProjectTasks(project, toolchainUnderTest, languageTaskSuffix, additionalTestTaskNames)
    }

    /**
     * Returns the tasks for the root project.
     */
    ProjectTasks getTasks() {
        return new ProjectTasks('', toolchainUnderTest, languageTaskSuffix, additionalTestTaskNames)
    }

    String[] getAdditionalTestTaskNames() {
        return []
    }

    static class ProjectTasks {
        private final String project
        private final AvailableToolChains.InstalledToolChain toolChainUnderTest
        private final String languageTaskSuffix
        private String architecture = null
        private String operatingSystemFamily = null
        private final String[] additionalTestTaskNames

        ProjectTasks(String project, AvailableToolChains.InstalledToolChain toolChainUnderTest, String languageTaskSuffix, String[] additionalTestTaskNames) {
            this.toolChainUnderTest = toolChainUnderTest
            this.project = project
            this.languageTaskSuffix = languageTaskSuffix
            this.additionalTestTaskNames = additionalTestTaskNames
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

        VariantTasks withBuildType(String buildType) {
            return new VariantTasks() {
                @Override
                protected String getBuildType() {
                    return buildType
                }
            }
        }

        private withProject(String t) {
            project + ":" + t
        }

        ProjectTasks withOperatingSystemFamily(String operatingSystemFamily) {
            this.operatingSystemFamily = operatingSystemFamily
            return this
        }

        abstract class VariantTasks {
            protected abstract String getBuildType()

            String getCompile() {
                return withProject("compile${buildType}${variant}${languageTaskSuffix}")
            }

            String getLink() {
                return withProject("link${buildType}${variant}")
            }

            String getCreate() {
                return withProject("create${buildType}${variant}")
            }

            String getInstall() {
                return withProject("install${buildType}${variant}")
            }

            String getAssemble() {
                return withProject("assemble${buildType}${variant}")
            }

            List<String> getAllToCreate() {
                return [compile, create]
            }

            List<String> getAllToLink() {
                return [compile, link]
            }

            List<String> getAllToInstall() {
                return allToLink + [install]
            }

            List<String> getAllToAssemble() {
                return allToLink + [assemble]
            }

            List<String> getAllToAssembleWithInstall() {
                return allToInstall + [assemble]
            }
        }

        class DebugTasks extends VariantTasks {
            @Override
            protected String getBuildType() {
                return "Debug"
            }

            List<String> getAllToInstall() {
                return allToLink + [install]
            }

            List<String> getAllToAssemble() {
                return allToLink + [assemble]
            }
        }

        class ReleaseTasks extends VariantTasks {
            @Override
            protected String getBuildType() {
                return "Release"
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
                return super.allToLink + strip
            }

            List<String> getAllToAssemble() {
                return super.allToAssemble + extract
            }

            List<String> getAllToAssembleWithInstall() {
                return super.allToAssembleWithInstall + extract
            }
        }

        class TestTasks extends VariantTasks {
            @Override
            protected String getBuildType() {
                return "Test"
            }

            List<String> getRun() {
                return [withProject("runTest${variant}")]
            }

            List<String> getRelocate() {
                return [withProject("relocateMainForTest${variant}")]
            }

            List<String> getAllToLink() {
                return [*additionalTestTaskNames.collect { withProject(it) }, compile, link]
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
