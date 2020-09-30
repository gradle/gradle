/*
 * Copyright 2020 the original author or authors.
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
import gradlebuild.basics.extension.BuildJvms
import gradlebuild.basics.repoRoot

plugins {
    `java-base`
}

val testJavaHomePropertyName = "testJavaHome"
val testJavaVersionPropertyName = "testJavaVersion"

val testJavaHomePath = providers.gradleProperty(testJavaHomePropertyName).forUseAtConfigurationTime()
    .orElse(providers.systemProperty(testJavaHomePropertyName).forUseAtConfigurationTime())
    .orElse(providers.environmentVariable(testJavaHomePropertyName).forUseAtConfigurationTime())
val testJavaHome = repoRoot().dir(testJavaHomePath)
val testJavaVersion = providers.gradleProperty(testJavaVersionPropertyName).forUseAtConfigurationTime()
    .orElse(providers.systemProperty(testJavaVersionPropertyName).forUseAtConfigurationTime())
    .orElse(providers.environmentVariable(testJavaVersionPropertyName).forUseAtConfigurationTime())

extensions.create<BuildJvms>("buildJvms", javaInstalls, testJavaHome, testJavaVersion)
