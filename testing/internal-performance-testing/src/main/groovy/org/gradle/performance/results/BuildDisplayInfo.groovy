/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.results

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@CompileStatic
@EqualsAndHashCode
@ToString
class BuildDisplayInfo {

    final String projectName
    final String displayName
    final List<String> tasksToRun
    final List<String> cleanTasks
    final List<String> args
    final List<String> gradleOpts
    final Boolean daemon

    BuildDisplayInfo(String projectName, String displayName, List<String> tasksToRun, List<String> cleanTasks, List<String> args, List<String> gradleOpts, Boolean daemon) {
        this.projectName = projectName
        this.displayName = displayName
        this.tasksToRun = tasksToRun
        this.cleanTasks = cleanTasks
        this.args = args
        this.gradleOpts = gradleOpts
        this.daemon = daemon
    }
}
