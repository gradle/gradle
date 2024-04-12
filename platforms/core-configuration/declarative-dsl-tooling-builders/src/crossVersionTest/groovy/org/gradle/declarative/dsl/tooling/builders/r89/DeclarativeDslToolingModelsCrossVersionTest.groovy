/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.declarative.dsl.tooling.builders.r89

import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.plugin.PluginBuilder

@TargetGradleVersion(">=8.9")
@ToolingApiVersion('>=8.9')
class DeclarativeDslToolingModelsCrossVersionTest extends ToolingApiSpecification {

    def setup(){
        settingsFile.delete() //we are using a declarative settings file
    }

    def 'can obtain model containing project schema'() {
        given:
        file("settings.gradle.something") << """
            rootProject.name = "test"
            include(":a")
            include(":b")
        """

        file("a/build.gradle.something") << """
            plugins {
                id("java")
            }
        """

        file("b/build.gradle.something") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(project(\":a\"))
                api(project(\":a\"))
                compileOnly(project(\":a\"))
                runtimeOnly(project(\":a\"))
                testImplementation(project(\":a\"))
                testCompileOnly(project(\":a\"))
            }
        """

        when:
        DeclarativeSchemaModel model = toolingApi.withConnection() { connection -> connection.getModel(DeclarativeSchemaModel.class) }

        then:
        model != null

        def schema = model.getProjectSchema()
        !schema.dataClassesByFqName.isEmpty()
    }

    def 'schema contains custom software type from included build'() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.something") << """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }
        """

        file("build.gradle.something") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        DeclarativeSchemaModel model = toolingApi.withConnection() { connection -> connection.getModel(DeclarativeSchemaModel.class) }

        then:
        model != null

        def schema = model.getProjectSchema()
        def topLevelReceiverType = schema.topLevelReceiverType
        def topLevelFunctions = topLevelReceiverType.memberFunctions.collect { toString() }
        !topLevelFunctions.find { it.contains("simpleName=testSoftwareType") }
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareType() {
        return """
            testSoftwareType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """
    }

    PluginBuilder withSoftwareTypePlugins() {
        def pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.addPluginId("com.example.test-software-type-impl", "SoftwareTypeImplPlugin")
        pluginBuilder.addPluginId("com.example.another-software-type-impl", "AnotherSoftwareTypeImplPlugin")
        pluginBuilder.addPluginId("com.example.test-software-type", "SoftwareTypeRegistrationPlugin")

        pluginBuilder.file("src/main/java/org/gradle/test/TestSoftwareTypeExtension.java") << """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Adding;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            @Restricted
            public abstract class TestSoftwareTypeExtension {
                private final Foo foo;

                @Inject
                public TestSoftwareTypeExtension(ObjectFactory objects) {
                    this.foo = objects.newInstance(Foo.class);
                    this.foo.getBar().set("bar");

                    getId().convention("<no id>");
                }

                @Restricted
                public abstract Property<String> getId();

                public Foo getFoo() {
                    return foo;
                }

                @Configuring
                public void foo(Action<? super Foo> action) {
                    action.execute(foo);
                }

                public abstract static class Foo {
                    public Foo() {
                        this.getBar().convention("nothing");
                    }

                    @Restricted
                    public abstract Property<String> getBar();
                }
            }
        """

        pluginBuilder.file("src/main/java/org/gradle/test/AnotherSoftwareTypeExtension.java") << """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Adding;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            @Restricted
            public abstract class AnotherSoftwareTypeExtension extends TestSoftwareTypeExtension {
                @Inject
                public AnotherSoftwareTypeExtension(ObjectFactory objects) {
                    super(objects);
                }
            }
        """

        pluginBuilder.file("src/main/java/org/gradle/test/SoftwareTypeImplPlugin.java") << """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import ${SoftwareType.class.name};
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.tasks.Nested;
            import javax.inject.Inject;

            abstract public class SoftwareTypeImplPlugin implements Plugin<Project> {
                @Inject
                abstract protected ObjectFactory getObjectFactory();

                @SoftwareType(name="testSoftwareType", modelPublicType=TestSoftwareTypeExtension.class)
                abstract public TestSoftwareTypeExtension getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    TestSoftwareTypeExtension extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("printConfiguration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            System.out.println("bar = " + extension.getFoo().getBar().get());
                        });
                    });
                }
            }
        """
        pluginBuilder.file("src/main/java/org/gradle/test/AnotherSoftwareTypeImplPlugin.java") << """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import ${SoftwareType.class.name};
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.tasks.Nested;
            import javax.inject.Inject;

            abstract public class AnotherSoftwareTypeImplPlugin implements Plugin<Project> {
                @Inject
                abstract protected ObjectFactory getObjectFactory();

                @SoftwareType(name="anotherSoftwareType", modelPublicType=AnotherSoftwareTypeExtension.class)
                abstract public AnotherSoftwareTypeExtension getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    AnotherSoftwareTypeExtension extension = getTestSoftwareTypeExtension();
                }
            }
        """

        pluginBuilder.file("src/main/java/org/gradle/test/SoftwareTypeRegistrationPlugin.java") << """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.internal.SettingsInternal;
            import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

            abstract public class SoftwareTypeRegistrationPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) {
                    ((SettingsInternal)target).getServices().get(SoftwareTypeRegistry.class).register(SoftwareTypeImplPlugin.class);
                    ((SettingsInternal)target).getServices().get(SoftwareTypeRegistry.class).register(AnotherSoftwareTypeImplPlugin.class);
                }
            }
        """

        return pluginBuilder
    }

}
