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

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.features.internal.builders.DefinitionBuilder
import org.gradle.features.internal.builders.Language
import org.gradle.features.internal.builders.PropertyTypeDeclaration
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Specification
import spock.lang.TempDir

class DefinitionBuilderTest extends Specification {
    @TempDir
    File tempDirFile

    TestFile getTempDir() { new TestFile(tempDirFile) }

    def "generates interface definition with properties"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            implementsDefinition("FooBuildModel") {
                property "barProcessed", String
            }
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("public interface TestProjectTypeDefinition extends Definition<TestProjectTypeDefinition.ModelType>")
        content.contains("Property<String> getId();")
        content.contains("Foo getFoo();")
        !content.contains("@HiddenInDefinition")
        !content.contains("void foo(Action<? super Foo> action)")
        content.contains("interface Foo extends Definition<FooBuildModel>")
        content.contains("Property<String> getBar();")
        content.contains("interface FooBuildModel extends BuildModel")
        content.contains("Property<String> getBarProcessed();")
        content.contains("interface ModelType extends BuildModel")
    }

    def "generates abstract class definition"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            implementsDefinition("FooBuildModel") {
                property "barProcessed", String
            }
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("public abstract class TestProjectTypeDefinition implements Definition<TestProjectTypeDefinition.ModelType>")
        content.contains("private final Foo foo;")
        content.contains("@Inject")
        content.contains("public TestProjectTypeDefinition(ObjectFactory objects)")
        content.contains("objects.newInstance(Foo.class)")
        content.contains("public abstract Property<String> getId();")
        content.contains("public Foo getFoo()")
        content.contains("public abstract static class Foo")
    }

    def "emits maybe<X>Configured scaffolding when showConfigureInvocations is enabled"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.showConfigureInvocations()
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("private boolean isFooConfigured = false;")
        content.contains("isFooConfigured = true;")
        content.contains("public String maybeFooConfigured()")
        content.contains('"(foo is configured)"')
    }

    def "omits maybe<X>Configured scaffolding by default"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        !content.contains("isFooConfigured")
        !content.contains("maybeFooConfigured")
        !content.contains("is configured")
    }

    def "showConfigureInvocations is a no-op on INTERFACE shape"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.INTERFACE)
        builder.buildModel("ModelType") { property "id", String }
        builder.showConfigureInvocations()
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        !content.contains("isFooConfigured")
        !content.contains("maybeFooConfigured")
        !content.contains("is configured")
    }

    def "generates definition with injected services"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.injectedService("objects", ObjectFactory)
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("@Inject")
        content.contains("ObjectFactory getObjects();")
    }

    def "generates definition with nested injected services"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            injectedService "objects", ObjectFactory
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("interface Foo")
        content.contains("ObjectFactory getObjects();")
        content.contains("Property<String> getBar();")
    }

    def "generates definition with NDOC property"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.ndoc("foos", "Foo") {
            property "x", Integer
            property "y", Integer
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("NamedDomainObjectContainer<Foo> getFoos();")
        content.contains("public abstract static class Foo implements Named")
    }

    def "generates definition with out-projected NDOC"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.ndoc("foos", "Foo") {
            outProjected()
            property "x", Integer
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("NamedDomainObjectContainer<? extends Foo> getOutFoos()")
    }

    def "generates definition with NDOC containing definitions"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.ndoc("sources", "Source") {
            implementsDefinition("SourceModel") {
                property "sourceDir", String
            }
            property "sourceDir", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("interface Source extends Definition<Source.SourceModel>, Named")
        content.contains("interface SourceModel extends BuildModel")
    }

    def "generates definition with no build model"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.noBuildModel()
        builder.property("text", String)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").text

        then:
        content.contains("public interface FeatureDefinition extends Definition<BuildModel.None>")
        !content.contains("interface ModelType")
        !content.contains("interface FeatureModel")
    }

    def "generates definition with separate implementation type"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.implementationType("TestProjectTypeDefinitionImpl")
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def publicContent = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text
        def implContent = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinitionImpl.java").text

        then:
        publicContent.contains("public interface TestProjectTypeDefinition")
        implContent.contains("public abstract class TestProjectTypeDefinitionImpl implements TestProjectTypeDefinition")
        implContent.contains("Property<String> getNonPublic();")
    }

    def "generates definition with parent definition"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property "bar", String
        }
        builder.parentDefinition {
            injectedService "objects", ObjectFactory
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def childContent = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text
        def parentContent = new File(tempDir, "src/main/java/org/gradle/test/ParentTestProjectTypeDefinition.java").text

        then:
        childContent.contains("public interface TestProjectTypeDefinition extends ParentTestProjectTypeDefinition")
        parentContent.contains("ObjectFactory getObjects();")
    }

    def "generates definition with build model having separate impl type"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.buildModel("FeatureModel") {
            property "text", String
            implementationType "FeatureModelImpl"
        }
        builder.property("text", String)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").text

        then:
        content.contains("interface FeatureModel extends BuildModel")
        content.contains("interface FeatureModelImpl extends FeatureModel")
    }

    def "generates definition with DirectoryProperty"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.buildModel("FeatureModel") {
            property "text", String
            property "dir", DirectoryProperty
        }
        builder.property("text", String)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").text

        then:
        content.contains("DirectoryProperty getDir();")
    }

    def "generates definition with read-only property"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.buildModel("FeatureModel") { property "text", String }
        builder.readOnlyProperty("dir", Directory)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").text

        then:
        content.contains("Directory getDir();")
        !content.contains("Property<Directory>")
    }

    def "generates definition with Java Bean-style properties"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.buildModel("FeatureModel") { property "text", String }
        builder.javaBeanProperty("dir", Directory)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").text

        then:
        content.contains("Directory getDir();")
        content.contains("void setDir(Directory value);")
    }

    def "provides correct fully qualified class names"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }

        expect:
        builder.fullyQualifiedPublicTypeClassName == "org.gradle.test.TestProjectTypeDefinition"
        builder.fullyQualifiedBuildModelClassName == "org.gradle.test.TestProjectTypeDefinition.ModelType"
        builder.buildModelFullPublicClassName == "TestProjectTypeDefinition.ModelType"
    }

    def "provides BuildModel.None when no build model"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.noBuildModel()

        expect:
        builder.fullyQualifiedBuildModelClassName == null
        builder.buildModelFullPublicClassName == "org.gradle.features.binding.BuildModel.None"
    }

    def "generates build model mapping from properties"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)

        expect:
        builder.getBuildModelMapping(Language.JAVA).contains("model.getId().set(definition.getId());")
        builder.getBuildModelMapping(Language.KOTLIN).contains("model.id.set(definition.id)")
        !builder.getBuildModelMapping(Language.KOTLIN).contains(";")
    }

    def "generates abstract class definition with undiscoverable property"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        // showConfigureInvocations() is enabled so the negative assertions below prove
        // that the undiscoverable branch suppresses scaffolding even when it is requested.
        builder.showConfigureInvocations()
        builder.property("id", String)
        builder.undiscoverable("foo", "Foo") {
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("public abstract class TestProjectTypeDefinition implements Definition<TestProjectTypeDefinition.ModelType>")
        content.contains("private final Foo foo;")
        !content.contains("private boolean isFooConfigured")
        content.contains("@Inject")
        content.contains("public TestProjectTypeDefinition(ObjectFactory objects)")
        content.contains("objects.newInstance(Foo.class)")
        !content.contains("public Foo getFoo()")
        !content.contains("isFooConfigured = true")
        !content.contains("maybeFooConfigured()")
        content.contains("public void foo(Action<? super Foo> action)")
        content.contains("public interface Foo")
        !content.contains("public abstract static class Foo")
    }

    def "inlines user-supplied initialization code for undiscoverable property in constructor"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.undiscoverable("foo", "Foo") {
            property "bar", String
            initializeWith('foo.getBar().set("default");')
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        def constructorBody = content =~ /public TestProjectTypeDefinition\(ObjectFactory objects\)\s*\{([^}]*)}/
        constructorBody.find()
        def body = constructorBody.group(1)
        body.contains("this.foo = objects.newInstance(Foo.class);")
        body.contains('foo.getBar().set("default");')
        body.indexOf("this.foo = objects.newInstance(Foo.class);") < body.indexOf('foo.getBar().set("default");')
    }

    def "generates abstract class definition with undiscoverable property implementing Definition"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.undiscoverable("foo", "Foo") {
            implementsDefinition("FooBuildModel") {
                property "barProcessed", String
            }
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("private final Foo foo;")
        !content.contains("public Foo getFoo()")
        content.contains("public void foo(Action<? super Foo> action)")
        content.contains("public interface Foo extends Definition<Foo.FooBuildModel>")
        content.contains("Property<String> getBar();")
        content.contains("public interface FooBuildModel extends BuildModel")
        content.contains("Property<String> getBarProcessed();")
        !content.contains("public abstract static class Foo")
    }

    def "undiscoverable property rejects INTERFACE shape"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }

        when:
        builder.undiscoverable("foo", "Foo") {
            property "bar", String
        }

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("undiscoverable")
        e.message.contains("ABSTRACT_CLASS")
    }

    def "uses custom build model mapping when provided"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.buildModel("FeatureModel") { property "text", String }
        builder.property("text", String)
        builder.buildModel {
            mapping("model.getText().set(parent.getText().map(text -> text + \" \" + definition.getText().get()));")
        }

        expect:
        builder.getBuildModelMapping(Language.JAVA).contains("model.getText().set(parent.getText()")
    }

    def "generates nested build model mapping for @Nested type implementing Definition"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            implementsDefinition("FooBuildModel") {
                property "bar", String
            }
            property "bar", String
        }

        expect:
        def java = builder.getBuildModelMapping(Language.JAVA)
        java.contains("model.getId().set(definition.getId());")
        java.contains("context.getBuildModel(definition.getFoo()).getBar().set(definition.getFoo().getBar());")

        def kotlin = builder.getBuildModelMapping(Language.KOTLIN)
        kotlin.contains("model.id.set(definition.id)")
        kotlin.contains("context.getBuildModel(definition.foo).bar.set(definition.foo.bar)")
        !kotlin.contains(";")
    }

    def "skips nested properties that don't match exactly"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            implementsDefinition("FooBuildModel") {
                property "barProcessed", String
            }
            property "bar", String
        }

        expect:
        def java = builder.getBuildModelMapping(Language.JAVA)
        !java.contains("getBar")
        !java.contains("barProcessed")
        !java.contains("context.getBuildModel")
    }

    def "generates nested build model mapping wrapped in configureEach for NDOC"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.ndoc("sources", "Source") {
            implementsDefinition("SourceModel") {
                property "sourceDir", String
            }
            property "sourceDir", String
        }

        expect:
        def java = builder.getBuildModelMapping(Language.JAVA)
        java.contains("definition.getSources().configureEach(source -> {")
        java.contains("context.getBuildModel(source).getSourceDir().set(source.getSourceDir());")
        java.contains("});")

        def kotlin = builder.getBuildModelMapping(Language.KOTLIN)
        kotlin.contains("definition.sources.configureEach { source ->")
        kotlin.contains("context.getBuildModel(source).sourceDir.set(source.sourceDir)")
    }

    def "skips nested build model mapping for undiscoverable type implementing Definition"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.undiscoverable("foo", "Foo") {
            implementsDefinition("FooBuildModel") {
                property "bar", String
            }
            property "bar", String
        }

        expect:
        def java = builder.getBuildModelMapping(Language.JAVA)
        java.contains("model.getId().set(definition.getId());")
        !java.contains("getFoo")
        !java.contains("context.getBuildModel")
    }

    def "uses custom nested build model mapping when provided"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            implementsDefinition("FooBuildModel") {
                property "bar", String
                mapping("// custom nested mapping")
            }
            property "bar", String
        }

        expect:
        def java = builder.getBuildModelMapping(Language.JAVA)
        java.contains("// custom nested mapping")
        !java.contains("context.getBuildModel")
    }

    def "does not generate nested mapping when nested type does not implement Definition"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property "bar", String
        }

        expect:
        def java = builder.getBuildModelMapping(Language.JAVA)
        !java.contains("context.getBuildModel")
    }

    def "emits annotation on simple property getter"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String) {
            annotations("@Incubating")
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("@Incubating\n                Property<String> getId();")
    }

    def "emits multiple annotations on getter preserving declared order"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String) {
            annotations("@Incubating", "@org.gradle.api.tasks.Optional")
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        def incubatingIndex = content.indexOf("@Incubating")
        def optionalIndex = content.indexOf("@org.gradle.api.tasks.Optional")
        def getterIndex = content.indexOf("Property<String> getId();")
        incubatingIndex > 0
        optionalIndex > incubatingIndex
        getterIndex > optionalIndex
    }

    def "produces byte-identical output when annotations list is empty"() {
        given:
        def withoutClosure = new DefinitionBuilder("TestProjectTypeDefinition")
        withoutClosure.buildModel("ModelType") { property "id", String }
        withoutClosure.property("id", String)

        def withEmptyClosure = new DefinitionBuilder("TestProjectTypeDefinition")
        withEmptyClosure.buildModel("ModelType") { property "id", String }
        withEmptyClosure.property("id", String) { annotations() }

        expect:
        withEmptyClosure.getPublicTypeClassContent() == withoutClosure.getPublicTypeClassContent()
    }

    def "emits annotations before @Nested on nested type getter"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("foo", "Foo") {
            annotations("@Incubating")
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        def incubatingIndex = content.indexOf("@Incubating")
        def nestedIndex = content.indexOf("@Nested")
        def getterIndex = content.indexOf("Foo getFoo();")
        incubatingIndex > 0
        nestedIndex > incubatingIndex
        getterIndex > nestedIndex
    }

    def "emits annotations on NDOC getter in interface shape"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.ndoc("sources", "Source") {
            annotations("@Incubating")
            property "path", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("@Incubating\n                public abstract NamedDomainObjectContainer<Source> getSources();")
    }

    def "emits annotations on NDOC getter in abstract class shape"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.ndoc("sources", "Source") {
            annotations("@Incubating")
            property "path", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("@Incubating\n                public abstract NamedDomainObjectContainer<Source> getSources();")
    }

    def "emits annotations on abstract class shape property getter"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String) {
            annotations("@Incubating")
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("@Incubating\n                public abstract Property<String> getId();")
    }

    def "emits annotations on abstract javaBean getter but not setter"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.buildModel("FeatureModel") { property "text", String }
        builder.javaBeanProperty("dir", Directory) {
            annotations("@Incubating")
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").text

        then:
        content.contains("@Incubating\n                Directory getDir();")
        def setterLine = content.readLines().find { it.contains("setDir(") }
        setterLine != null
        !setterLine.contains("@Incubating")
    }

    def "emits annotations on concrete javaBean getter but not field or setter"() {
        given:
        def builder = new DefinitionBuilder("FeatureDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("FeatureModel") { property "text", String }
        builder.javaBeanProperty("dir", Directory) {
            shape(org.gradle.features.internal.builders.PropertyDeclaration.Shape.CONCRETE)
            annotations("@Incubating")
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/FeatureDefinition.java").text

        then:
        def fieldLine = content.readLines().find { it.contains("private Directory dir;") }
        def getterLines = content.split("\n")
        def getterIndex = getterLines.findIndexOf { it.contains("public Directory getDir()") }
        def incubatingIndex = getterLines.findIndexOf { it.contains("@Incubating") }
        def setterLine = getterLines.find { it.contains("setDir(Directory value)") }

        fieldLine != null
        !fieldLine.contains("@Incubating")
        getterIndex > 0
        incubatingIndex > 0
        incubatingIndex < getterIndex
        setterLine != null
        !setterLine.contains("@Incubating")
    }

    def "emits annotations on build model property getter"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") {
            property("id", String) { annotations("@Incubating") }
        }
        builder.property("id", String)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        def modelStart = content.indexOf("public interface ModelType extends BuildModel")
        def annotationIndex = content.indexOf("@Incubating", modelStart)
        def getterIndex = content.indexOf("Property<String> getId();", modelStart)
        modelStart > 0
        annotationIndex > modelStart
        getterIndex > annotationIndex
    }

    def "emits annotations on NDOC element property getter when implementing Definition"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.ndoc("sources", "Source") {
            implementsDefinition("SourceModel") {
                property "sourceDir", String
            }
            property("sourceDir", String) {
                annotations("@Incubating")
            }
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        def sourceStart = content.indexOf("public interface Source extends Definition")
        def annotationIndex = content.indexOf("@Incubating", sourceStart)
        def getterIndex = content.indexOf("public abstract Property<String> getSourceDir();", sourceStart)
        sourceStart > 0
        annotationIndex > sourceStart
        getterIndex > annotationIndex
    }

    def "emits annotations before @Override on implementation class getter"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.implementationType("TestProjectTypeDefinitionImpl")
        builder.property("id", String)
        builder.property("foo", "Foo") {
            annotations("@Incubating")
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def implContent = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinitionImpl.java").text

        then:
        def incubatingIndex = implContent.indexOf("@Incubating")
        def overrideIndex = implContent.indexOf("@Override")
        def getterIndex = implContent.indexOf("public Foo getFoo()")
        incubatingIndex > 0
        overrideIndex > incubatingIndex
        getterIndex > overrideIndex
    }

    def "throws when annotations are declared on undiscoverable top-level declaration"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }

        when:
        builder.undiscoverable("foo", "Foo") {
            annotations("@Incubating")
            property "bar", String
        }

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("undiscoverable")
        e.message.contains("no public getter")
    }

    def "allows annotations on sub-nested declarations inside undiscoverable closure"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.undiscoverable("foo", "Foo") {
            property("bar", "Bar") {
                annotations("@Incubating")
                property "baz", String
            }
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("@Incubating")
    }

    def "shared-ref property does not emit an inner type body in the definition file"() {
        given:
        def sharedRef = new PropertyTypeDeclaration(typeName: "SourceSet")
        sharedRef.property("name", String)

        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("srcSet", sharedRef)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("SourceSet getSrcSet();")
        !content.contains("interface SourceSet")
        !content.contains("class SourceSet")
    }

    def "shared-ref property on abstract-class definition emits field, ObjectFactory init, and getter"() {
        given:
        def sharedRef = new PropertyTypeDeclaration(typeName: "SourceSet")
        sharedRef.property("name", String)

        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("srcSet", sharedRef)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("private final SourceSet srcSet;")
        content.contains("this.srcSet = objects.newInstance(SourceSet.class);")
        content.contains("public SourceSet getSrcSet()")
        !content.contains("interface SourceSet")
        !content.contains("abstract static class SourceSet")
    }

    def "shared-ref build model property emits plain getter without Property<> wrapping"() {
        given:
        def sharedRef = new PropertyTypeDeclaration(typeName: "SourceSet")
        sharedRef.property("name", String)

        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") {
            property "id", String
            property "srcSet", sharedRef
        }
        builder.property("id", String)
        builder.property("srcSet", sharedRef)

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        def modelStart = content.indexOf("public interface ModelType extends BuildModel")
        def getterIndex = content.indexOf("SourceSet getSrcSet();", modelStart)
        modelStart > 0
        getterIndex > modelStart
        !content.substring(modelStart, getterIndex).contains("@Nested")
        !content.substring(modelStart, getterIndex).contains("Property<SourceSet>")
    }

    def "auto-maps shared-ref scalars when definition and build model share the same ref"() {
        given:
        def sharedRef = new PropertyTypeDeclaration(typeName: "SourceSet")
        sharedRef.property("name", String)
        sharedRef.property("path", String)

        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") {
            property "id", String
            property "srcSet", sharedRef
        }
        builder.property("id", String)
        builder.property("srcSet", sharedRef)

        expect:
        def java = builder.getBuildModelMapping(Language.JAVA)
        java.contains("model.getId().set(definition.getId());")
        java.contains("model.getSrcSet().getName().set(definition.getSrcSet().getName());")
        java.contains("model.getSrcSet().getPath().set(definition.getSrcSet().getPath());")
        !java.contains("context.getBuildModel")

        def kotlin = builder.getBuildModelMapping(Language.KOTLIN)
        kotlin.contains("model.srcSet.name.set(definition.srcSet.name)")
        !kotlin.contains(";")
    }

    def "skips auto-mapping when the shared type implements Definition"() {
        given:
        def sharedRef = new PropertyTypeDeclaration(typeName: "SourceSet", implementsDefinition: true)
        sharedRef.property("name", String)
        sharedRef.buildModel = new org.gradle.features.internal.builders.BuildModelDeclaration(className: "SourceSetModel")
        sharedRef.buildModel.property("nameProcessed", String)

        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") {
            property "id", String
            property "srcSet", sharedRef
        }
        builder.property("id", String)
        builder.property("srcSet", sharedRef)

        expect:
        def java = builder.getBuildModelMapping(Language.JAVA)
        java.contains("model.getId().set(definition.getId());")
        !java.contains("model.getSrcSet")
        !java.contains("context.getBuildModel")
    }

    def "property(String, Class, Closure) overload routes to PropertyDeclaration not nested type"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String) {
            annotations "@Incubating"
        }

        expect:
        builder.properties.size() == 1
        builder.properties[0].name == "id"
        builder.properties[0].type == String
        builder.properties[0].allAnnotations == ["@Incubating"]
        builder.nestedTypes.isEmpty()
    }

    // --- Per-nested-type shape override ---

    def "nested type with shape(ABSTRACT_CLASS) in interface outer emits abstract static class body"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            shape DefinitionBuilder.Shape.ABSTRACT_CLASS
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("public interface TestProjectTypeDefinition extends Definition<")
        content.contains("@Nested")
        content.contains("Foo getFoo();")
        content.contains("public abstract static class Foo")
        content.contains("public abstract Property<String> getBar();")
        !content.contains("public interface Foo")
    }

    def "sub-nested with shape(ABSTRACT_CLASS) inside abstract-class nested emits @Nested public abstract accessor"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            shape DefinitionBuilder.Shape.ABSTRACT_CLASS
            property "bar", String
            property("inner", "Inner") {
                shape DefinitionBuilder.Shape.ABSTRACT_CLASS
                property "value", String
            }
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("public abstract static class Foo")
        content.contains("@Nested")
        content.contains("public abstract Inner getInner();")
        content.contains("public abstract static class Inner")
    }

    def "sub-nested inherits INTERFACE shape from interface-outer default"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property("inner", "Inner") {
                property "value", String
            }
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        // Both levels stay interfaces (existing byte-for-byte behavior preserved).
        content.contains("public interface Foo")
        content.contains("public interface Inner")
    }

    def "abstract-class outer with shape(INTERFACE) nested emits @Nested abstract getter and interface body"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            shape DefinitionBuilder.Shape.INTERFACE
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("public abstract class TestProjectTypeDefinition")
        content.contains("public abstract Foo getFoo();")
        // No field, no ctor init for the interface-shaped nested
        !content.contains("private final Foo foo;")
        !content.contains("objects.newInstance(Foo.class)")
        !content.contains("public Foo getFoo() {")
        content.contains("public interface Foo")
        !content.contains("public abstract static class Foo")
    }

    def "abstract-class outer with default nested emits sub-nested bodies (gap-fix)"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            property "bar", String
            property("inner", "Inner") {
                property "value", String
            }
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("public abstract static class Foo")
        content.contains("public abstract Inner getInner();")
        content.contains("public abstract static class Inner")
        content.contains("public abstract Property<String> getValue();")
    }

    def "ABSTRACT_CLASS-shaped nested with implementsDefinition uses implements keyword"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            shape DefinitionBuilder.Shape.ABSTRACT_CLASS
            implementsDefinition("FooBuildModel") {
                property "barProcessed", String
            }
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def content = new File(tempDir, "src/main/java/org/gradle/test/TestProjectTypeDefinition.java").text

        then:
        content.contains("public abstract static class Foo implements Definition<FooBuildModel>")
        content.contains("public interface FooBuildModel extends BuildModel")
        content.contains("Property<String> getBarProcessed();")
    }

    def "showConfigureInvocations rejects effectively-INTERFACE nested"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }
        builder.showConfigureInvocations()
        builder.property("foo", "Foo") {
            shape DefinitionBuilder.Shape.INTERFACE
            property "bar", String
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("showConfigureInvocations")
        e.message.contains("foo")
    }

    def "shape(...) rejected on NDOC declaration"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }

        when:
        builder.ndoc("sources", "Source") {
            shape DefinitionBuilder.Shape.ABSTRACT_CLASS
        }

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("shape(...)")
        e.message.contains("NDOC")
    }

    def "shape(...) rejected on undiscoverable declaration"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)
        builder.buildModel("ModelType") { property "id", String }

        when:
        builder.undiscoverable("foo", "Foo") {
            shape DefinitionBuilder.Shape.INTERFACE
        }

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("shape(...)")
        e.message.contains("undiscoverable")
    }

    def "shape(...) rejected on shared-ref property"() {
        given:
        def sharedRef = new PropertyTypeDeclaration(typeName: "SourceSet", isSharedRef: true)

        when:
        sharedRef.shape(DefinitionBuilder.Shape.ABSTRACT_CLASS)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("shape(...)")
        e.message.contains("shared-ref")
    }

    def "parent-definition respects nested shape override"() {
        given:
        def builder = new DefinitionBuilder("TestProjectTypeDefinition")
        builder.buildModel("ModelType") { property "id", String }
        builder.property("id", String)
        builder.property("foo", "Foo") {
            shape DefinitionBuilder.Shape.ABSTRACT_CLASS
            property "bar", String
        }
        builder.parentDefinition {
            injectedService "objects", ObjectFactory
        }

        when:
        def pluginBuilder = new PluginBuilder(tempDir)
        builder.build(pluginBuilder)
        def parentContent = new File(tempDir, "src/main/java/org/gradle/test/ParentTestProjectTypeDefinition.java").text

        then:
        // Parent renders as interface (child is empty extends-interface), but the nested
        // with shape(ABSTRACT_CLASS) still renders as an abstract static class body inside it.
        parentContent.contains("public interface ParentTestProjectTypeDefinition extends Definition<")
        parentContent.contains("public abstract static class Foo")
    }
}
