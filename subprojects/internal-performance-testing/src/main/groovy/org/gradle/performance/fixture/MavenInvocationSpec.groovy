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
import org.gradle.wrapper.GradleUserHomeLookup

@CompileStatic
@EqualsAndHashCode
class MavenInvocationSpec implements InvocationSpec {

    private static final String DEFAULT_MAVEN_VERSION = "3.5.0"

    final MavenInstallation installation
    final String mavenVersion
    final File mavenHome
    final File workingDirectory
    final List<String> tasksToRun
    final List<String> jvmOpts
    final List<String> mavenOpts
    final List<String> args
    final List<String> cleanTasks

    MavenInvocationSpec(MavenInstallation installation, File workingDirectory, List<String> tasksToRun, List<String> jvmOpts, List<String> mavenOpts, List<String> args, List<String> cleanTasks) {
        this.installation = installation
        this.mavenVersion = installation.version
        this.mavenHome = installation.home
        this.workingDirectory = workingDirectory
        this.tasksToRun = tasksToRun
        this.jvmOpts = jvmOpts
        this.mavenOpts = mavenOpts
        this.args = args
        this.cleanTasks = cleanTasks
    }

    @Override
    boolean isExpectFailure() {
        throw new UnsupportedOperationException()
    }

    static InvocationBuilder builder() {
        return new InvocationBuilder()
    }

    InvocationBuilder withBuilder() {
        InvocationBuilder builder = new InvocationBuilder()
        builder.mavenVersion(mavenVersion)
        builder.mavenHome(mavenHome)
        builder.workingDirectory(workingDirectory)
        builder.tasksToRun.addAll(this.tasksToRun)
        builder.jvmOpts(this.jvmOpts)
        builder.mavenOpts(this.mavenOpts)
        builder.args(this.args)
        builder.cleanTasks.addAll(cleanTasks)
        builder
    }

    static class InvocationBuilder implements InvocationSpec.Builder {
        String mavenVersion
        File mavenHome
        File workingDirectory
        List<String> tasksToRun = []
        List<String> jvmOpts = []
        List<String> mavenOpts = []
        List<String> args = []
        List<String> cleanTasks = []

        InvocationBuilder mavenVersion(String mavenVersion) {
            this.mavenVersion = mavenVersion
            this
        }

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

        InvocationBuilder tasksToRun(Iterable<String> taskToRun) {
            this.tasksToRun.addAll(taskToRun)
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

        InvocationBuilder mavenOpts(String... args) {
            this.mavenOpts.addAll(Arrays.asList(args))
            this
        }

        InvocationBuilder mavenOpts(Iterable<String> args) {
            this.mavenOpts.addAll(args)
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

        InvocationBuilder cleanTasks(String... cleanTasks) {
            this.cleanTasks(Arrays.asList(cleanTasks))
        }

        InvocationBuilder cleanTasks(Iterable<String> cleanTasks) {
            this.cleanTasks.addAll(cleanTasks)
            this
        }

        @Override
        InvocationSpec.Builder expectFailure() {
            throw new UnsupportedOperationException()
        }

        MavenInvocationSpec build() {
            def mavenInstallation
            if (mavenVersion != null && mavenHome != null) {
                assertMavenHomeAndVersionMatch()
            } else if (mavenHome != null) {
                mavenVersion = MavenInstallation.probeVersion(mavenHome)
            } else {
                mavenVersion = mavenVersion ?: DEFAULT_MAVEN_VERSION
                mavenInstallation = eventuallyDownloadMavenHome()
                mavenHome = mavenInstallation.home
            }
            assert mavenVersion != null
            assert mavenHome != null
            assert workingDirectory != null
            mavenInstallation = mavenInstallation ?: new MavenInstallation(mavenVersion, mavenHome)
            return new MavenInvocationSpec(mavenInstallation, workingDirectory, tasksToRun.asImmutable(), jvmOpts.asImmutable(), mavenOpts.asImmutable(), args.asImmutable(), cleanTasks.asImmutable())
        }

        private void assertMavenHomeAndVersionMatch() {
            def probedVersion = MavenInstallation.probeVersion(mavenHome)
            assert mavenVersion == probedVersion
        }

        private MavenInstallation eventuallyDownloadMavenHome() {
            def installsRoot = new File(GradleUserHomeLookup.gradleUserHome(), "caches${File.separator}maven-installs")
            def downloader = new MavenInstallationDownloader(installsRoot)
            downloader.getMavenInstallation(mavenVersion)
        }
    }
}
