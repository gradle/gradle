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

    String[] compileTasks(String project = '', String variant) {
        ["${project}:discoverInputsForCompile${variant}Cpp", "${project}:compile${variant}Cpp"] as String[]
    }

    String linkTask(String project = '', String variant) {
        "${project}:link${variant}"
    }

    String installTask(String project = '', String variant) {
        "${project}:install${variant}"
    }

    String[] compileAndLinkTasks(List<String> projects = [''], String variant) {
        projects.collect { project ->
            [*compileTasks(project, variant), linkTask(project, variant)]
        }.flatten()
    }

    String getDebug() {
        DEBUG
    }

    String getRelease() {
        RELEASE
    }

}
