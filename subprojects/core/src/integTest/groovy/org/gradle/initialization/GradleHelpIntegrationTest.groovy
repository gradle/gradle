/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradleHelpIntegrationTest extends AbstractIntegrationSpec {
    def "gradle -h is useful"() {
        // This test doesn't actually start a daemon, but we need to require a daemon to avoid the in-process executer
        executer.requireDaemon().requireIsolatedDaemons()

        when:
        succeeds("-h")
        then:
        output == """
To see help contextual to the project, use gradle help

To see more detail about a task, run gradle help --task <task>

To see a list of available tasks, run gradle tasks

USAGE: gradle [option...] [task...]

-?, -h, --help                     Shows this help message.
-a, --no-rebuild                   Do not rebuild project dependencies.
--build-cache                      Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.
--no-build-cache                   Disables the Gradle build cache.
--configuration-cache              Enables the configuration cache. Gradle will try to reuse the build configuration from previous builds.
--no-configuration-cache           Disables the configuration cache.
--configuration-cache-problems     Configures how the configuration cache handles problems (fail or warn). Defaults to fail.
--configure-on-demand              Configure necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds. [incubating]
--no-configure-on-demand           Disables the use of configuration on demand. [incubating]
--console                          Specifies which type of console output to generate. Values are 'plain', 'colored', 'auto' (default), 'rich' or 'verbose'.
--continue                         Continue task execution after a task failure.
--no-continue                      Stop task execution after a task failure.
-D, --system-prop                  Set system property of the JVM (e.g. -Dmyprop=myvalue).
-d, --debug                        Log in debug mode (includes normal stacktrace).
--daemon                           Uses the Gradle daemon to run the build. Starts the daemon if not running.
--no-daemon                        Do not use the Gradle daemon to run the build. Useful occasionally if you have configured Gradle to always run with the daemon by default.
--develocity-default-url           Specify the default URL of the Develocity server to use for Build Scans. If the Develocity plugin is configured, this value is ignored.
--export-keys                      Exports the public keys used for dependency verification.
-F, --dependency-verification      Configures the dependency verification mode. Values are 'strict', 'lenient' or 'off'.
--foreground                       Starts the Gradle daemon in the foreground.
-g, --gradle-user-home             Specifies the Gradle user home directory. Defaults to ~/.gradle
-I, --init-script                  Specify an initialization script.
-i, --info                         Set log level to info.
--include-build                    Include the specified build in the composite.
-M, --write-verification-metadata  Generates checksums for dependencies used in the project (comma-separated list)
-m, --dry-run                      Run the builds with all task actions disabled.
--max-workers                      Configure the number of concurrent workers Gradle is allowed to use.
--offline                          Execute the build without accessing network resources.
-P, --project-prop                 Set project property for the build script (e.g. -Pmyprop=myvalue).
-p, --project-dir                  Specifies the start directory for Gradle. Defaults to current directory.
--parallel                         Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.
--no-parallel                      Disables parallel execution to build projects.
--priority                         Specifies the scheduling priority for the Gradle daemon and all processes launched by it. Values are 'normal' (default) or 'low'
--problems-report                  (Experimental) enables HTML problems report
--no-problems-report               (Experimental) disables HTML problems report
--profile                          Profile build execution time and generates a report in the <build_dir>/reports/profile directory.
--project-cache-dir                Specify the project-specific cache directory. Defaults to .gradle in the root project directory.
--property-upgrade-report          (Experimental) Runs build with experimental property upgrade report.
-q, --quiet                        Log errors only.
--refresh-keys                     Refresh the public keys used for dependency verification.
--rerun-tasks                      Ignore previously cached task results.
-S, --full-stacktrace              Print out the full (very verbose) stacktrace for all exceptions.
-s, --stacktrace                   Print out the stacktrace for all exceptions.
--scan                             Generate a Build Scan (powered by Develocity).
                                   Build Scan and Develocity are registered trademarks of Gradle, Inc.
                                   For more information, please visit https://gradle.com/develocity/product/build-scan/.
--no-scan                          Disables the creation of a Build Scan.
--status                           Shows status of running and recently stopped Gradle daemon(s).
--stop                             Stops the Gradle daemon if it is running.
-t, --continuous                   Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change.
--task-graph                       (Experimental) Print task graph instead of executing tasks.
-U, --refresh-dependencies         Refresh the state of dependencies.
--update-locks                     Perform a partial update of the dependency lock, letting passed in module notations change version. [incubating]
-V, --show-version                 Print version info and continue.
-v, --version                      Print version info and exit.
-w, --warn                         Set log level to warn.
--warning-mode                     Specifies which mode of warnings to generate. Values are 'all', 'fail', 'summary'(default) or 'none'
--watch-fs                         Enables watching the file system for changes, allowing data about the file system to be re-used for the next build.
--no-watch-fs                      Disables watching the file system.
--write-locks                      Persists dependency resolution for locked configurations, ignoring existing locking information if it exists
-x, --exclude-task                 Specify a task to be excluded from execution.
--                                 Signals the end of built-in options. Gradle parses subsequent parameters as only tasks or task options.

"""
    }
}
