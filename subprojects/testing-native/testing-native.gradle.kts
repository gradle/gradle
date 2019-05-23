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
plugins {
    `java-library`
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":diagnostics"))
    implementation(project(":reporting"))
    implementation(project(":platformBase"))
    implementation(project(":platformNative"))
    implementation(project(":languageNative"))
    implementation(project(":testingBase"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("inject"))

    testImplementation(project(":files"))

    integTestRuntimeOnly(project(":ideNative"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":platformNative")
    from(":diagnostics")
    from(":platformBase")
    from(":testingBase")
    from(":languageNative")
    from(":ide")
}
