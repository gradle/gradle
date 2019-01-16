import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.unittestandcompile.ModuleType

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

/*
 * The model management core.
 */
plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${BuildEnvironment.kotlinVersion}")

    api(project(":baseServices"))
    api(project(":coreApi"))
    api(library("inject"))
    api(library("groovy"))

    implementation(project(":baseServicesGroovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("asm"))

    integTestRuntimeOnly(project(":apiMetadata"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":coreApi")
    from(":diagnostics", "testFixtures")
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/model/internal/core/**",
        "org/gradle/model/internal/inspect/**",
        "org/gradle/api/internal/tasks/**",
        "org/gradle/model/internal/manage/schema/**",
        "org/gradle/model/internal/type/**",
        "org/gradle/api/internal/plugins/*"
    ))
}
