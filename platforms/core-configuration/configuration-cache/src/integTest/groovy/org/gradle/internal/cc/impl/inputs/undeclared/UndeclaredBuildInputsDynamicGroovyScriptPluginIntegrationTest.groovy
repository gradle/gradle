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

package org.gradle.internal.cc.impl.inputs.undeclared

class UndeclaredBuildInputsDynamicGroovyScriptPluginIntegrationTest extends AbstractUndeclaredBuildInputsIntegrationTest implements GroovyPluginImplementation {
    def script = file("plugin.gradle")

    @Override
    String getLocation() {
        return "Script 'plugin.gradle'"
    }

    @Override
    void buildLogicApplication(BuildInputRead read) {
        groovyDsl(script, read)
        buildFile << """
            apply from: "plugin.gradle"
        """
    }
}
