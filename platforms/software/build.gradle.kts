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
    subproject(project(":antlr"))
    subproject(project(":build-init"))
    subproject(project(":dependency-management"))
    subproject(project(":distributions-publishing"))
    subproject(project(":ivy"))
    subproject(project(":maven"))
    subproject(project(":platform-base"))
    subproject(project(":plugins-distribution"))
    subproject(project(":plugins-version-catalog"))
    subproject(project(":publish"))
    subproject(project(":reporting"))
    subproject(project(":resources"))
    subproject(project(":resources-gcs"))
    subproject(project(":resources-http"))
    subproject(project(":resources-s3"))
    subproject(project(":resources-sftp"))
    subproject(project(":security"))
    subproject(project(":signing"))
    subproject(project(":test-suites-base"))
    subproject(project(":testing-base"))
    subproject(project(":version-control"))
}
