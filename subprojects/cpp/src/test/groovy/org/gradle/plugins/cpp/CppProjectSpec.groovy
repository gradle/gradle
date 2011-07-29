/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.cpp

import spock.lang.*
import org.gradle.api.Project
import org.gradle.util.HelperUtil

class CppProjectSpec extends Specification {

    Project project = HelperUtil.createRootProject()

    private assertProject() {
        assert project != null : "You haven't created a project"
    }

    def methodMissing(String name, args) {
        assertProject()
        project."$name"(*args)
    }

    def propertyMissing(String name) {
        project."$name"
    }

    def propertyMissing(String name, value) {
        project."$name" = value
    }

    def applyPlugin() {
        apply plugin: "cpp"
    }

    def getResourceFile(path) {
        def url = getClass().classLoader.getResource(path)
        new File(url.toURI())
    }

}