/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.java

enum JavaTestProject {

    LARGE_MONOLITHIC_JAVA_PROJECT("largeMonolithicJavaProject", "2g"),
    LARGE_JAVA_MULTI_PROJECT("largeJavaMultiProject", "2g"),
    MEDIUM_JAVA_MULTI_PROJECT_WITH_TEST_NG("mediumJavaMultiProjectWithTestNG", "2g")

    private String projectName
    private String daemonMemory

    JavaTestProject(String projectName, String daemonMemory) {
        this.projectName = projectName
        this.daemonMemory = daemonMemory
    }

    String getProjectName() {
        return projectName
    }

    String getDaemonMemory() {
        return daemonMemory
    }

    @Override
    String toString() {
        return projectName
    }
}
