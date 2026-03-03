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

package org.gradle.launcher.cli

class HelpFixture {

    static final String DEFAULT_OUTPUT = '''
To see help contextual to the project, use gradle help

To see more detail about a task, run gradle help --task <task>

To see a list of available tasks, run gradle tasks

USAGE: gradle [option...] [task...]

-?, -h, --help                     Shows this help message.
-a, --no-rebuild                   Disables rebuilding of project dependencies.
--build-cache                      Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.
--no-build-cache                   Disables the Gradle build cache.
--configuration-cache              Enables the configuration cache. Gradle will try to reuse the build configuration from previous builds.
--no-configuration-cache           Disables the configuration cache.
--configuration-cache-problems     Configures how the configuration cache handles problems (fail or warn). Supported values are 'warn', or 'fail' (default).
--configure-on-demand              Configures necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds. [incubating]
--no-configure-on-demand           Disables the use of configuration on demand. [incubating]
--console                          Specifies which type of console output to generate. Supported values are 'plain', 'colored', 'auto' (default), 'rich', or 'verbose'.
--console-unicode                  Specifies which character types are allowed in the console output. Supported values are 'auto' (default), 'disable', or 'enable'.
--continue                         Continues task execution after a task failure.
--no-continue                      Stops task execution after a task failure.
-D, --system-prop                  Sets a JVM system property (for example, -Dmyprop=myvalue).
-d, --debug                        Sets log level to debug. Includes the normal stacktrace.
--daemon                           Uses the Gradle daemon to run the build. Starts the daemon if it is not running.
--no-daemon                        Runs the build without the Gradle daemon. Useful occasionally if you have configured Gradle to always run with the daemon by default.
--export-keys                      Exports the public keys used for dependency verification.
-F, --dependency-verification      Configures the dependency verification mode. Supported values are 'strict', 'lenient', or 'off'.
--foreground                       Starts the Gradle daemon in the foreground.
-g, --gradle-user-home             Specifies the Gradle user home directory. Default is ~/.gradle.
-I, --init-script                  Specifies an initialization script.
-i, --info                         Sets the log level to info.
--include-build                    Includes the specified build in the composite.
-M, --write-verification-metadata  Generates checksums for dependencies used in the project. Accepts a comma-separated list.
-m, --dry-run                      Runs the build with all task actions disabled.
--max-workers                      Configures the maximum number of concurrent workers Gradle is allowed to use.
--offline                          Runs the build without accessing network resources.
-P, --project-prop                 Sets a project property for the build script (for example, -Pmyprop=myvalue).
-p, --project-dir                  Specifies the start directory for Gradle. Default is the current directory.
--parallel                         Builds projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.
--no-parallel                      Disables parallel project execution.
--priority                         Specifies the scheduling priority for the Gradle daemon and all processes launched by it. Supported values are 'normal' (default) or 'low'.
--problems-report                  Enables the HTML problems report. [incubating]
--no-problems-report               Disables the HTML problems report. [incubating]
--profile                          Profiles build execution time. Generates a report in the <build_dir>/reports/profile directory.
--project-cache-dir                Specifies the project-specific cache directory. Default is .gradle in the root project directory.
--property-upgrade-report          Runs the build with the experimental property upgrade report. [incubating]
-q, --quiet                        Logs errors only.
--refresh-keys                     Refreshes the public keys used for dependency verification.
--rerun-tasks                      Ignores previously cached task results.
-S, --full-stacktrace              Prints the full (very verbose) stacktrace for all exceptions.
-s, --stacktrace                   Prints the stacktrace for all exceptions.
--scan                             Generates a Build Scan (powered by Develocity).
                                   Build Scan and Develocity are registered trademarks of Gradle, Inc.
                                   For more information, please visit https://gradle.com/develocity/product/build-scan/.
--no-scan                          Disables the creation of a Build Scan.
--status                           Shows the status of running and recently stopped Gradle daemons.
--stop                             Stops the Gradle daemon if it is running.
-t, --continuous                   Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change.
--task-graph                       Prints the task graph instead of executing tasks.
-U, --refresh-dependencies         Refreshes the state of dependencies.
--update-locks                     Performs a partial update of the dependency lock. Allows passed-in module notations to change version. [incubating]
-V, --show-version                 Prints version information and continues.
-v, --version                      Prints version information and exits.
-w, --warn                         Sets the log level to warn.
--warning-mode                     Specifies which mode of warnings to generate. Supported values are 'all', 'fail', 'summary' (default), or 'none'.
--watch-fs                         Enables file system watching. Reuses file system data for subsequent builds.
--no-watch-fs                      Disables file system watching.
--write-locks                      Persists dependency resolution for locked configurations. Ignores existing locking information if it exists.
-x, --exclude-task                 Specifies a task to exclude from execution.
--                                 Signals the end of built-in options. Parses subsequent parameters as tasks or task options only.

'''
}
