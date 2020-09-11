/*
 * Copyright 2010 the original author or authors.
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
    id("gradlebuild.internal.java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":native"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.ant)
    implementation(libs.asm)
    implementation(libs.asmTree)
    implementation(libs.junit)
    implementation(libs.spock)
    implementation(libs.jsoup)
    implementation(libs.testcontainersSpock)

    runtimeOnly(libs.bytebuddy)
}
