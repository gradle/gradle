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

package org.gradle.configurationcache.inputs.undeclared

import spock.lang.Ignore
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle-private/issues/3252")
class UndeclaredBuildInputsStaticGroovyBuildSrcIntegrationTest extends AbstractUndeclaredBuildInputsIntegrationTest implements GroovyPluginImplementation {
    @Override
    String getLocation() {
        return "Plugin class 'SneakyPlugin'"
    }

    @Override
    void buildLogicApplication(SystemPropertyRead read) {
        staticGroovyPlugin(file("buildSrc/src/main/groovy/SneakyPlugin.groovy"), read)
        buildFile << """
            apply plugin: SneakyPlugin
        """
    }

    @Ignore
    def "can reference static methods via instance variables"() {
        expect: false
    }

    @Ignore
    def "can reference methods from groovy closure"() {
        expect: false
    }
}
