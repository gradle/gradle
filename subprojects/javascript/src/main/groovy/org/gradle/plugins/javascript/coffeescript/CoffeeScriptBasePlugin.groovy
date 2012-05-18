/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.plugins.javascript.coffeescript

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.plugins.javascript.base.JavaScriptExtension
import org.gradle.plugins.javascript.base.JavaScriptBasePlugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency

class CoffeeScriptBasePlugin implements Plugin<Project> {

    public static final String DEFAULT_COFFEE_SCRIPT_JS_DEPENDENCY = "org.coffeescript:coffee-script-js:1.3.3@js"

    void apply(Project project) {
        project.apply(plugin: JavaScriptBasePlugin)
        JavaScriptExtension jsExtension = project.extensions.getByType(JavaScriptExtension)

        Configuration coffeeScriptJsConfiguration = project.configurations.detachedConfiguration()
        Dependency defaultCoffeeScriptJsDependency = project.dependencies.create(DEFAULT_COFFEE_SCRIPT_JS_DEPENDENCY)

        CoffeeScriptExtension csExtension = jsExtension.extensions.create(
                CoffeeScriptExtension.NAME, CoffeeScriptExtension,
                coffeeScriptJsConfiguration, defaultCoffeeScriptJsDependency
        )
    }
}
