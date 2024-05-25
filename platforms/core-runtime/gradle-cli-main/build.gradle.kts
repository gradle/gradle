import gradlebuild.startscript.tasks.GradleStartScriptGenerator

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
    id("gradlebuild.application-entrypoint")
    id("gradlebuild.start-scripts")
}

description = "Java 6-compatible entry point of the `gradle` command. Boostraps the Gradle client implementation in :launcher."

gradlebuildJava.usedForStartup()

application {
    implementationTitle = "Gradle CLI"
    mainClassName = "org.gradle.launcher.GradleMain"

    // Exclude META-INF resources from Guava etc. added via transitive dependencies
    excludeFromDependencies("META-INF/*")

    // Exclude these to avoid conflicts when the JAR is included in functional test classpath
    excludeFromDependencies("org/slf4j/Logger.class")
    excludeFromDependencies("org/slf4j/ILoggerFactory.class")

    outputJarName { "gradle-cli-main-${it.version}.jar" }
}

dependencies {
    implementation(projects.javaLanguageExtensions)
    implementation(project(":build-process-services"))

    // For start script generation
    agentsClasspath(project(":instrumentation-agent"))
}
