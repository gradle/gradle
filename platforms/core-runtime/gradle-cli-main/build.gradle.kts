/*
 * Copyright 2024 the original author or authors.
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

plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.launchable-jar")
    id("gradlebuild.start-scripts")
}

description = "Entry point of the `gradle` command. Bootstraps the implementation in :gradle-cli."

gradleModule {
    targetRuntimes {
        usedInClient = true
    }
}

tasks.jar {
    manifest {
        // Allow launcher to access JNI: https://openjdk.org/jeps/472
        attributes("Enable-Native-Access" to "ALL-UNNAMED")
    }
}

app {
    mainClassName = "org.gradle.launcher.GradleMain"
}

dependencies {
    implementation(projects.buildProcessServices)

    implementation(libs.jspecify)

    manifestClasspath(projects.stdlibJavaExtensions)
    manifestClasspath(projects.buildProcessServices)
    manifestClasspath(projects.baseServices)
    manifestClasspath(projects.classloaders)
    manifestClasspath(projects.concurrent)
    manifestClasspath(projects.serviceLookup)

    agentsClasspath(projects.instrumentationAgent)
}
