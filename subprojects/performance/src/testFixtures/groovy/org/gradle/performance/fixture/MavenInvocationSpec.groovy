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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode
class MavenInvocationSpec implements InvocationSpec {

    final File mavenHome
    final File workingDirectory
    final List<String> tasksToRun
    final List<String> jvmOpts
    final List<String> args

    MavenInvocationSpec(File mavenHome, File workingDirectory, List<String> tasksToRun, List<String> jvmOpts, List<String> args) {
        this.mavenHome = mavenHome
        this.workingDirectory = workingDirectory
        this.tasksToRun = tasksToRun
        this.jvmOpts = jvmOpts
        this.args = args
    }

    static InvocationBuilder builder() {
        return new InvocationBuilder()
    }

    InvocationBuilder withBuilder() {
        InvocationBuilder builder = new InvocationBuilder()
        builder.mavenHome(mavenHome)
        builder.workingDirectory(workingDirectory)
        builder.tasksToRun.addAll(this.tasksToRun)
        builder.jvmOpts(this.jvmOpts)
        builder.args(this.args)
        builder
    }

    static class InvocationBuilder implements InvocationSpec.Builder {
        File mavenHome
        File workingDirectory
        List<String> tasksToRun = []
        List<String> jvmOpts = []
        List<String> args = []

        InvocationBuilder mavenHome(File home) {
            this.mavenHome = home
            this
        }

        InvocationBuilder workingDirectory(File workingDirectory) {
            this.workingDirectory = workingDirectory
            this
        }

        InvocationBuilder tasksToRun(String... taskToRun) {
            this.tasksToRun.addAll(Arrays.asList(taskToRun))
            this
        }

        InvocationBuilder jvmOpts(String... args) {
            this.jvmOpts.addAll(Arrays.asList(args))
            this
        }

        InvocationBuilder jvmOpts(Iterable<String> args) {
            this.jvmOpts.addAll(args)
            this
        }

        InvocationBuilder args(String... args) {
            this.args.addAll(Arrays.asList(args))
            this
        }

        InvocationBuilder args(Iterable<String> args) {
            this.args.addAll(args)
            this
        }

        MavenInvocationSpec build() {
            assert mavenHome != null
            assert workingDirectory != null

            return new MavenInvocationSpec(mavenHome, workingDirectory, tasksToRun.asImmutable(), jvmOpts.asImmutable(), args.asImmutable())
        }
    }
}
