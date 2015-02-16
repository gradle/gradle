/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance.fixture

import com.google.common.collect.ImmutableList
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class BuildSpecification {
    final String projectName
    final String displayName
    final String[] tasksToRun
    final String[] args
    final String[] gradleOpts
    final boolean useDaemon

    BuildSpecification(String projectName, String displayName, String[] tasksToRun, String[] args, String[] gradleOpts, boolean useDaemon) {
        this.projectName = projectName
        this.displayName = displayName
        this.tasksToRun = tasksToRun
        this.args = args
        this.gradleOpts = gradleOpts
        this.useDaemon = useDaemon
    }

    String getDisplayName() {
        displayName ?: projectName
    }

    static Builder forProject(String projectName) {
        new Builder(projectName)
    }

    static class Builder {
        private String projectName
        private String displayName

        private ImmutableList.Builder<String> tasksToRun = ImmutableList.builder()
        private ImmutableList.Builder<String> args = ImmutableList.builder()
        private ImmutableList.Builder<String> gradleOpts = ImmutableList.builder()
        private boolean useDaemon

        Builder(String projectName) {
            this.projectName = projectName
        }

        Builder forProject(String projectName) {
            this.projectName = projectName
            this
        }

        Builder displayName(String displayName) {
            this.displayName = displayName
            this
        }

        Builder tasksToRun(String... tasksToRun) {
            this.tasksToRun.add(tasksToRun)
            this
        }

        Builder args(String... args) {
            this.args.add(args)
            this
        }

        Builder gradleOpts(String... gradleOpts) {
            this.gradleOpts.add(gradleOpts)
            this
        }

        Builder useDaemon() {
            this.useDaemon = true
            this
        }

        BuildSpecification build() {
            String[] tasks = tasksToRun.build().toArray()
            String[] gradleOpts = gradleOpts.build().toArray()
            String[] args = args.build().toArray()

            new BuildSpecification(projectName, displayName, tasks, args, gradleOpts, useDaemon)
        }
    }
}
