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

package org.gradle.integtests.tooling.fixture

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

class ToolingApiConnector {
    GradleConnector connector
    private final OutputStream stdout
    private final OutputStream stderr

    ToolingApiConnector(GradleConnector connector, OutputStream stdout, OutputStream stderr) {
        this.stderr = stderr
        this.stdout = stdout
        this.connector = connector
    }

    def connect() {
        new ToolingApiConnection(connector.connect(), stdout, stderr) as ProjectConnection
    }

    def searchUpwards(boolean searchUpwards) {
        connector.searchUpwards(searchUpwards)
        this
    }

    def methodMissing(String name, args) {
        connector."$name"(*args)
    }

    def propertyMissing(String name, value) {
        connector."$name" = value
    }

    def propertyMissing(String name) {
        connector."$name"
    }

    def forProjectDirectory(File projectDir) {
        connector.forProjectDirectory(projectDir)
        connector
    }

    def disconnect() {
        connector.disconnect()
    }
}
