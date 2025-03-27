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

plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Worker RequestHandler that hosts long-running daemon server which can execute arbitrary WorkerAction requests. " +
    "These classes are loaded in a separate worker daemon process and should have a minimal dependency set."

// TODO: These classes _are_ used in workers, but require Java 8. We should
// enable this flag in Gradle 9.0 when workers target Java 8.
// gradlebuildJava.usedInWorkers()

dependencies {

    api(projects.classloaders)
    api(projects.coreApi)
    api(projects.modelCore)
    api(projects.requestHandlerWorker)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.snapshots)

    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.baseServices)
    implementation(projects.buildProcessServices)
    implementation(projects.concurrent)
    implementation(projects.fileCollections)
    implementation(projects.fileOperations)
    implementation(projects.fileTemp)
    implementation(projects.hashing)
    implementation(projects.persistentCache)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.buildOperations)
    implementation(projects.messaging)
    implementation(projects.problemsApi)
    implementation(projects.workerMain)

    // The worker infrastructure should _not_ depend on :core. :core contains much
    // of the Gradle daemon implementation, and brings in a much larger classpath
    // than what the workers require. Furthermore, the daemon and workers have different
    // JVM version requirements. Depending on :core from here restricts the daemon
    // from upgrading its target bytecode version.
    implementation(projects.core)

    implementation(libs.guava)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.hashing))

    integTestDistributionRuntimeOnly(projects.distributionsCore)

}
