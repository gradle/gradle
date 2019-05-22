import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"));
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
    `java-library`
}

val css by configurations.creating {
    // define a configuration that, when resolved, will look in
    // the producer for a publication that exposes CSS resources
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named("css-resources"))
    }
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":toolingApi"))
    implementation(project(":reporting"))
    implementation(project(":plugins"))
    implementation(project(":platformJvm"))
    implementation(project(":ear"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("inject"))

    testImplementation(project(":native"))
    testImplementation(project(":processServices"))
    testImplementation(project(":files"))
    testImplementation(project(":baseServicesGroovy"))
    testImplementation(project(":dependencyManagement"))
    testImplementation(testLibrary("jsoup"))

    integTestRuntimeOnly(project(":toolingApiBuilders"))

    css(project(":docs"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
}

tasks.processResources {
    from(css) {
        into("org/gradle/api/plugins/buildcomparison/render/internal/html")
        include("base.css")
    }
}
