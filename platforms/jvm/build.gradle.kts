/*
 * Copyright 2023 the original author or authors.
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
    id("gradlebuild.gradle-platform")
}

dependencies {
    subproject(project(":code-quality"))
    subproject(project(":distributions-jvm"))
    subproject(project(":ear"))
    subproject(project(":jacoco"))
    subproject(project(":java-compiler-plugin"))
    subproject(project(":java-platform"))
    subproject(project(":jvm-services"))
    subproject(project(":language-groovy"))
    subproject(project(":language-java"))
    subproject(project(":language-jvm"))
    subproject(project(":normalization-java"))
    subproject(project(":platform-jvm"))
    subproject(project(":plugins-groovy"))
    subproject(project(":plugins-java"))
    subproject(project(":plugins-java-base"))
    subproject(project(":plugins-jvm-test-fixtures"))
    subproject(project(":plugins-jvm-test-suite"))
    subproject(project(":plugins-test-report-aggregation"))
    subproject(project(":scala"))
    subproject(project(":testing-junit-platform"))
    subproject(project(":testing-jvm"))
    subproject(project(":testing-jvm-infrastructure"))
    subproject(project(":toolchains-jvm"))
    subproject(project(":war"))
}
