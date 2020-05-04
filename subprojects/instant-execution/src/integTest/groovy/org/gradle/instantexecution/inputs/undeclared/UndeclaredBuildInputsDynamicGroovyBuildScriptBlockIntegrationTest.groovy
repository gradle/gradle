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

package org.gradle.instantexecution.inputs.undeclared

class UndeclaredBuildInputsDynamicGroovyBuildScriptBlockIntegrationTest extends AbstractUndeclaredBuildInputsIntegrationTest implements GroovyPluginImplementation {
    @Override
    void buildLogicApplication() {
        buildFile << """
            buildscript {
                println("apply GET_PROPERTY = " + System.getProperty("GET_PROPERTY"))
                println("apply GET_PROPERTY_OR_DEFAULT = " + System.getProperty("GET_PROPERTY_OR_DEFAULT", "default"))
            }
            tasks.register("thing") {
                doLast {
                    println("task GET_PROPERTY = " + System.getProperty("GET_PROPERTY"))
                    println("task GET_PROPERTY_OR_DEFAULT = " + System.getProperty("GET_PROPERTY_OR_DEFAULT", "default"))
                }
            }
        """
    }
}
