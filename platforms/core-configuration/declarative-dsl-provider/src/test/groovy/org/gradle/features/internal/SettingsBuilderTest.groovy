/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal

import org.gradle.features.internal.builders.DefinitionAndPluginBuilder
import org.gradle.features.internal.builders.Language
import org.gradle.features.internal.builders.SettingsBuilder
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Specification
import spock.lang.TempDir

class SettingsBuilderTest extends Specification {
    @TempDir
    File tempDirFile

    TestFile getTempDir() { new TestFile(tempDirFile) }

    def "generates settings plugin registering types and features (#language)"() {
        given:
        def settings = new SettingsBuilder()
        settings.language = sourceLanguage
        settings.pluginClassName = pluginClassName
        settings.registersProjectType("ProjectTypeImplPlugin")
        settings.registersProjectFeature("ProjectFeatureImplPlugin")

        when:
        def pb = new PluginBuilder(tempDir)
        settings.build(pb)
        def content = new File(tempDir, sourceFile).text

        then:
        content.contains(registrationAnnotation)
        content.contains(classDeclaration)
        content.contains("fun apply(settings: Settings)") || content.contains("void apply(Settings settings)")

        where:
        sourceLanguage  | pluginClassName                    | sourceFile                                                                                 | registrationAnnotation                                                                              | classDeclaration
        Language.JAVA   | "ProjectTypeRegistrationPlugin"   | "src/main/java/org/gradle/test/ProjectTypeRegistrationPlugin.java"                         | "@RegistersProjectFeatures({ ProjectTypeImplPlugin.class, ProjectFeatureImplPlugin.class })"        | "abstract public class ProjectTypeRegistrationPlugin implements Plugin<Settings>"
        Language.KOTLIN | "ProjectFeatureRegistrationPlugin" | "src/main/kotlin/org/gradle/test/ProjectFeatureRegistrationPlugin.kt"                      | "@RegistersProjectFeatures(ProjectTypeImplPlugin::class, ProjectFeatureImplPlugin::class)"          | "class ProjectFeatureRegistrationPlugin : Plugin<Settings>"
        language = sourceLanguage.name().toLowerCase()
    }

    def "generates settings plugin with model defaults (#language)"() {
        given:
        def type = DefinitionAndPluginBuilder.forProjectType("testProjectType")
        def settings = new SettingsBuilder()
        settings.language = sourceLanguage
        settings.pluginClassName = pluginClassName
        settings.registersProjectType("TestProjectTypeImplPlugin")
        settings.defaults {
            defaultFor(type) {
                property "id", "from-defaults"
                property "foo.bar", "default-bar"
            }
        }

        when:
        def pb = new PluginBuilder(tempDir)
        settings.build(pb)
        def content = new File(tempDir, sourceFile).text

        then:
        content.contains(defaultsAdd)
        content.contains(idConvention)
        content.contains(fooBarConvention)

        where:
        sourceLanguage  | pluginClassName                    | sourceFile                                                                                 | defaultsAdd                                                                                                      | idConvention                                      | fooBarConvention
        Language.JAVA   | "ProjectTypeRegistrationPlugin"   | "src/main/java/org/gradle/test/ProjectTypeRegistrationPlugin.java"                         | 'settings.getDefaults().add("testProjectType", TestProjectTypeDefinition.class, definition -> {'                 | 'definition.getId().convention("from-defaults");'  | 'definition.getFoo().getBar().convention("default-bar");'
        Language.KOTLIN | "ProjectFeatureRegistrationPlugin" | "src/main/kotlin/org/gradle/test/ProjectFeatureRegistrationPlugin.kt"                      | 'settings.defaults.add("testProjectType", TestProjectTypeDefinition::class.java) { definition ->'                | 'definition.id.convention("from-defaults")'        | 'definition.foo.bar.convention("default-bar")'
        language = sourceLanguage.name().toLowerCase()
    }
}
