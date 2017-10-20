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
    private static final String STATIC = 'Static'
    private static final String SHARED = 'Shared'

    String[] compileTasksDebug(String project = '') {
        compileTasks(project, DEBUG)
    }

    String linkTaskDebug(String project = '') {
        linkTask(project, DEBUG)
    }

    String installTaskDebug(String project = '') {
        installTask(project, DEBUG)
    }

    String installTaskDebugStatic(String project = '') {
        installTask(project, DEBUG, STATIC)
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

    String[] compileTasks(String project = '', String buildType, String linkage = SHARED) {
        ["${project}:depend${buildType}${linkage}Cpp", compileTask(project, buildType, linkage)] as String[]
    }

    String[] publishTasks(String project = '', String buildType, String linkage = SHARED) {
        ["${project}:generatePomFileFor${buildType}${linkage}Publication",
         "${project}:generateMetadataFileFor${buildType}${linkage}Publication",
         "${project}:publish${buildType}${linkage}PublicationToMavenRepository"] as String[]
    }

    String compileTask(String project = '', String buildType, String linkage = SHARED) {
        "${project}:compile${buildType}${linkage}Cpp"
    }

    String linkTask(String project = '', String buildType, String linkage = SHARED) {
        "${project}:link${buildType}${linkage}"
    }

    String createTask(String project = '', String buildType) {
        "${project}:create${buildType}Static"
    }

    String installTask(String project = '', String buildType, String linkage = SHARED) {
        "${project}:install${buildType}${linkage}"
    }

    String[] compileAndLinkTasks(List<String> projects = [''], String buildType) {
        projects.collect { project ->
            [*compileTasks(project, buildType), linkTask(project, buildType)]
        }.flatten()
    }

    String[] compileAndCreateTasks(List<String> projects = [''], String buildType) {
        projects.collect { project ->
            [*compileTasks(project, buildType, STATIC), createTask(project, buildType)]
        }.flatten()
    }

    String[] compileAndLinkStaticTasks(List<String> projects = [''], String buildType) {
        projects.collect { project ->
            [*compileTasks(project, buildType, STATIC), linkTask(project, buildType, STATIC)]
        }.flatten()
    }

    String getDebug() {
        DEBUG
    }

    String getRelease() {
        RELEASE
    }

    String getStaticLinkage() {
        STATIC
    }

    String getSharedLinkage() {
        SHARED
    }
}
