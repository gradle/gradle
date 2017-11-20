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

import org.gradle.nativeplatform.fixtures.AvailableToolChains

trait CppTaskNames {

    private static final String DEBUG = 'Debug'
    private static final String RELEASE = 'Release'

    String[] compileTasksDebug(String project = '') {
        compileTasks(project, DEBUG)
    }

    String linkTaskDebug(String project = '') {
        linkTask(project, DEBUG)
    }

    String installTaskDebug(String project = '') {
        installTask(project, DEBUG)
    }

    String[] compileTasksRelease(String project = '') {
        compileTasks(project, RELEASE)
    }

    String linkTaskRelease(String project = '') {
        linkTask(project, RELEASE)
    }

    String installTaskRelease(String project = '') {
        installTask(project, RELEASE)
    }

    String[] compileTasks(String project = '', String buildType) {
        ["${project}:depend${buildType}Cpp", compileTask(project, buildType)] as String[]
    }

    String compileTask(String project = '', String buildType) {
        "${project}:compile${buildType}Cpp"
    }

    String linkTask(String project = '', String buildType) {
        "${project}:link${buildType}"
    }

    String installTask(String project = '', String buildType) {
        "${project}:install${buildType}"
    }

    String[] compileAndLinkTasks(List<String> projects = [''], String buildType) {
        projects.collect { project ->
            [*compileTasks(project, buildType), linkTask(project, buildType)]
        }.flatten()
    }

    String[] stripSymbolsTasksRelease(String project = '', AvailableToolChains.InstalledToolChain toolChain) {
        return stripSymbolsTasks(project, RELEASE, toolChain)
    }

    String[] stripSymbolsTasks(String project = '', String buildType, AvailableToolChains.InstalledToolChain toolChain) {
        if (toolChain instanceof AvailableToolChains.InstalledVisualCpp) {
            return []
        } else {
            return ["${project}:extractSymbols${buildType}", "${project}:stripSymbols${buildType}"]
        }
    }

    String[] stripSymbolsTasks(List<String> projects, String buildType, AvailableToolChains.InstalledToolChain toolChain) {
        projects.collect { project ->
            [*stripSymbolsTasks(project, buildType, toolChain)]
        }.flatten()
    }

    String getDebug() {
        DEBUG
    }

    String getRelease() {
        RELEASE
    }

}
