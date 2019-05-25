/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    // TODO: re-enable if we are ready to do breaking changes, because this subproject includes classes migrated from the "plugins" subproject
    // id "gradlebuild.strict-compile"
    // id "gradlebuild.classycle"
}

dependencies {
    api(library("jsr305"))

    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":files"))
    implementation(project(":jvmServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":dependencyManagement"))
    implementation(project(":reporting"))
    implementation(project(":diagnostics"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJava"))
    implementation(project(":testingBase"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("asm"))
    implementation(library("junit"))
    implementation(library("testng"))
    implementation(library("inject"))
    implementation(library("bsh"))

    testImplementation(project(":baseServicesGroovy"))
    testImplementation("com.google.inject:guice:2.0") {
        because("This is for TestNG")
    }

    integTestRuntimeOnly(project(":testingJunitPlatform"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

tasks.named<Test>("test").configure {
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestClass*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ABroken*TestClass*.*")
}

testFixtures {
    from(":core")
    from(":testingBase")
    from(":diagnostics")
    from(":messaging")
    from(":baseServices")
    from(":platformNative")
}
