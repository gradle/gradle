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

package org.gradle.api.plugins.jvm.internal


import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class DefaultJvmLanguageUtilitiesTest extends AbstractProjectBuilderSpec {
    def "can register a new source directory set compiled with Java"() {
        project.plugins.apply(JavaBasePlugin)
        def extension = project.extensions.getByType(JavaPluginExtension)

        when:
        project.tasks.register("classes")
        def sourceSet = extension.sourceSets.create("foo")

        then:
        project.services.get(JvmLanguageUtilities).registerJvmLanguageSourceDirectory(sourceSet, "mylang") {
            it.withDescription("my test language")
            it.compiledWithJava {
                it.targetCompatibility = '8'
            }
        }
    }
}
