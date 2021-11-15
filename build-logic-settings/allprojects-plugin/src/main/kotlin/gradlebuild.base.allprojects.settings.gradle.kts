/*
 * Copyright 2021 the original author or authors.
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

/**
 * This plugin is responsible for applying a plugin to ALL projects.
 * Therefore, it should be used with diligence: it is very unlikely
 * that you want to add something here, rather than on a more specific
 * project kind, for example `gradlebuild.java-library`.
 *
 * This should be limited to validation or tasks which are independent
 * of the kind of project we're building.
 */
val enableValidation = providers.systemProperty("org.gradle.internal.validate.external.plugins")

gradle.beforeProject {
    if (enableValidation.map(String::toBoolean).orElse(false).get()) {
        // Add external plugin validation only for smoke tests of the Gradle build itself
        pluginManager.apply("validate-external-gradle-plugin")
    }
}
