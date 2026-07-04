/*
 * Copyright 2026 the original author or authors.
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

import gradlebuild.startscript.tasks.GradleStartScriptGenerator

plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.launchable-jar")
    id("gradlebuild.start-scripts")
}

description = "Standalone command line tool to list and stop Gradle daemons of the current version"

app {
    mainClassName = "org.gradle.launcher.daemon.tool.DaemonManagementTool"
}

dependencies {
    // The tool talks to daemons using only the daemon management API. Its command line parsing uses the
    // bootstrap `cli` module. It deliberately depends on nothing else - no client-services, launcher or
    // service-wiring internals.
    implementation(projects.cli)
    implementation(projects.daemonManagementApi)
}

gradleModule {
    requiredRuntimes {
        client = true
    }
    computedRuntimes {
        client = true
    }
}

// Ship as a distinct `bin/daemon-management-tool` launcher instead of the default `bin/gradle`.
tasks.named<GradleStartScriptGenerator>("startScripts") {
    scriptBaseName = "daemon-management-tool"
    applicationName = "Gradle Daemon Management Tool"
    optsEnvironmentVar = "DAEMON_MANAGEMENT_TOOL_OPTS"
}
