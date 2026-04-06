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

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.features.internal.builders.BindingDeclaration
import org.gradle.features.internal.builders.DefinitionBuilder
import org.gradle.features.internal.builders.Language
import org.gradle.features.internal.builders.PluginClassBuilder
import org.gradle.features.registration.TaskRegistrar
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Specification
import spock.lang.TempDir

class PluginClassBuilderTest extends Specification {
    @TempDir
    File tempDirFile

    TestFile getTempDir() { new TestFile(tempDirFile) }

    private PluginClassBuilder pluginFor(DefinitionBuilder defn, String name) {
        def plugin = new PluginClassBuilder()
        plugin.packageName = defn.packageName
        plugin.bindings.add(new BindingDeclaration(definition: defn, name: name))
        return plugin
    }

    private DefinitionBuilder standardTypeDefinition() {
        def defn = new DefinitionBuilder("TestProjectTypeDefinition")
        defn.buildModel("ModelType") { property "id", String }
        defn.property("id", String)
        defn.property("foo", "Foo") {
            implementsDefinition("FooBuildModel") { property "barProcessed", String }
            property "bar", String
        }
        return defn
    }

    private DefinitionBuilder standardFeatureDefinition() {
        def defn = new DefinitionBuilder("FeatureDefinition")
        defn.buildModel("FeatureModel") {
            property "text", String
            property "dir", DirectoryProperty
        }
        defn.property("text", String)
        defn.property("fizz", "Fizz") { property "buzz", String }
        return defn
    }

    def "generates project type plugin"() {
        given:
        def defn = standardTypeDefinition()
        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "ProjectTypeImplPlugin"

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeImplPlugin.java").text

        then:
        content.contains("@BindsProjectType(ProjectTypeImplPlugin.Binding.class)")
        content.contains("static class Binding implements ProjectTypeBinding")
        content.contains('builder.bindProjectType("testProjectType", TestProjectTypeDefinition.class, ProjectTypeImplPlugin.ApplyAction.class)')
        content.contains("static abstract class ApplyAction implements")
        content.contains("ProjectTypeApplyAction")
        content.contains("getTaskRegistrar()")
        content.contains('System.out.println("Binding " + TestProjectTypeDefinition.class.getSimpleName())')
        content.contains("model.getId().set(definition.getId());")
    }

    def "generates project type plugin with unsafe definition"() {
        given:
        def defn = standardTypeDefinition()
        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "ProjectTypeImplPlugin"
        plugin.unsafeDefinition()

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeImplPlugin.java").text

        then:
        content.contains(".withUnsafeDefinition()")
    }

    def "generates project type plugin with unsafe apply action"() {
        given:
        def defn = standardTypeDefinition()
        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "ProjectTypeImplPlugin"
        plugin.unsafeApplyAction()

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeImplPlugin.java").text

        then:
        content.contains(".withUnsafeApplyAction()")
    }

    def "generates project type plugin with custom services"() {
        given:
        def defn = standardTypeDefinition()
        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "ProjectTypeImplPlugin"
        plugin.applyAction { injectedService "project", Project }
        plugin.unsafeApplyAction()

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeImplPlugin.java").text

        then:
        content.contains("abstract protected Project getProject()")
        content.contains("getProject().getTasks()")
    }

    def "generates project type plugin with unknown service"() {
        given:
        def defn = standardTypeDefinition()
        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "ProjectTypeImplPlugin"
        plugin.applyAction { injectedService "unknown", TaskRegistrar }
        plugin.unsafeApplyAction()

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeImplPlugin.java").text

        then:
        content.contains("interface UnknownService extends")
        content.contains("getUnknownService()")
    }

    def "generates project type plugin with no bindings"() {
        given:
        def defn = standardTypeDefinition()
        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "NotAProjectTypePlugin"
        plugin.noBindings()

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/NotAProjectTypePlugin.java").text

        then:
        !content.contains("@BindsProjectType")
        content.contains("abstract public class NotAProjectTypePlugin implements Plugin<Project>")
    }

    def "generates project type plugin with multiple type bindings"() {
        given:
        def defn = standardTypeDefinition()
        def anotherDefn = new DefinitionBuilder("AnotherProjectTypeDefinition")
        anotherDefn.buildModel("ModelType") { property "name", String }
        anotherDefn.property("name", String)

        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "ProjectTypeImplPlugin"
        plugin.bindsType(anotherDefn, "anotherProjectType")

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeImplPlugin.java").text

        then:
        content.contains('builder.bindProjectType("testProjectType", TestProjectTypeDefinition.class, TestProjectTypeDefinitionApplyAction.class)')
        content.contains('builder.bindProjectType("anotherProjectType", AnotherProjectTypeDefinition.class, AnotherProjectTypeDefinitionApplyAction.class)')
    }

    def "generates project type plugin with eager reads"() {
        given:
        def defn = standardTypeDefinition()
        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "ProjectTypeImplPlugin"
        plugin.applyAction {
            injectedService "taskRegistrar", TaskRegistrar
            eagerlyReadDefinitionValues()
        }
        plugin.unsafeApplyAction()

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectTypeImplPlugin.java").text

        then:
        content.contains("definition.getId().get()")
        content.contains("definition.getFoo().getBar().get()")
        content.contains("printApplyTimeValues")
    }

    def "generates project feature plugin with definition binding"() {
        given:
        def defn = standardFeatureDefinition()
        def plugin = pluginFor(defn, "feature")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_FEATURE
        plugin.pluginClassName = "ProjectFeatureImplPlugin"
        plugin.bindsFeatureTo("TestProjectTypeDefinition")

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectFeatureImplPlugin.java").text

        then:
        content.contains("@BindsProjectFeature(ProjectFeatureImplPlugin.Binding.class)")
        content.contains("static class Binding implements ProjectFeatureBinding")
        content.contains('builder.bindProjectFeatureToDefinition(')
        content.contains("FeatureDefinition.class")
        content.contains("TestProjectTypeDefinition.class")
        content.contains("ApplyAction.class")
        content.contains("ProjectFeatureApplyAction")
        content.contains("getTaskRegistrar()")
        content.contains("getProjectFeatureLayout()")
        content.contains("getProviderFactory()")
    }

    def "generates project feature plugin with build model binding"() {
        given:
        def defn = standardFeatureDefinition()
        def plugin = pluginFor(defn, "feature")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_FEATURE
        plugin.pluginClassName = "ProjectFeatureImplPlugin"
        plugin.bindToBuildModel()
        plugin.bindsFeatureTo("org.gradle.test.TestProjectTypeDefinition.ModelType")

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectFeatureImplPlugin.java").text

        then:
        content.contains("builder.bindProjectFeatureToBuildModel(")
        content.contains("Definition<org.gradle.test.TestProjectTypeDefinition.ModelType>")
    }

    def "generates project feature plugin with no build model"() {
        given:
        def defn = new DefinitionBuilder("FeatureDefinition")
        defn.noBuildModel()
        defn.property("text", String)

        def plugin = pluginFor(defn, "feature")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_FEATURE
        plugin.pluginClassName = "ProjectFeatureImplPlugin"
        plugin.noBuildModel()
        plugin.bindsFeatureTo("TestProjectTypeDefinition")

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/java/org/gradle/test/ProjectFeatureImplPlugin.java").text

        then:
        content.contains("BuildModel.None")
        !content.contains("model.getText()")
    }

    def "generates Kotlin project type plugin"() {
        given:
        def defn = standardTypeDefinition()
        def plugin = pluginFor(defn, "testProjectType")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "ProjectTypeImplPlugin"
        plugin.language = Language.KOTLIN

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/kotlin/org/gradle/test/ProjectTypeImplPlugin.kt").text

        then:
        content.contains("class ProjectTypeImplPlugin : Plugin<Project>")
        content.contains("class Binding : ProjectTypeBinding")
        content.contains("ProjectTypeImplPlugin.ApplyAction::class")
        content.contains("abstract val taskRegistrar:")
        content.contains("override fun apply(")
    }

    def "generates Kotlin project feature plugin"() {
        given:
        def defn = standardFeatureDefinition()
        def plugin = pluginFor(defn, "feature")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_FEATURE
        plugin.pluginClassName = "ProjectFeatureImplPlugin"
        plugin.bindsFeatureTo("TestProjectTypeDefinition")
        plugin.language = Language.KOTLIN

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/kotlin/org/gradle/test/ProjectFeatureImplPlugin.kt").text

        then:
        content.contains("class ProjectFeatureImplPlugin : Plugin<Project>")
        content.contains("class Binding : ProjectFeatureBinding")
        content.contains("abstract class ApplyAction @Inject constructor() :")
        content.contains("ProjectFeatureApplyAction")
        content.contains("abstract val taskRegistrar:")
        content.contains("abstract val projectFeatureLayout:")
        content.contains("abstract val providerFactory:")
    }

    def "generates Kotlin reified project feature plugin"() {
        given:
        def defn = new DefinitionBuilder("FeatureDefinition")
        defn.noBuildModel()
        defn.property("text", String)

        def plugin = pluginFor(defn, "feature")
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_FEATURE
        plugin.pluginClassName = "ProjectFeatureImplPlugin"
        plugin.noBuildModel()
        plugin.bindingStyle = PluginClassBuilder.BindingStyle.REIFIED
        plugin.bindsFeatureTo("TestProjectTypeDefinition")
        plugin.language = Language.KOTLIN

        when:
        def pb = new PluginBuilder(tempDir)
        plugin.build(pb)
        def content = new File(tempDir, "src/main/kotlin/org/gradle/test/ProjectFeatureImplPlugin.kt").text

        then:
        content.contains("BuildModel.None")
        content.contains("import org.gradle.features.dsl.bindProjectFeature")
        content.contains("builder.bindProjectFeature(\"feature\", ProjectFeatureImplPlugin.ApplyAction::class)")
        !content.contains("::class.java")
    }
}
