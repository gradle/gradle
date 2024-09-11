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

import org.gradle.declarative.dsl.tooling.builders.AbstractDeclarativeDslToolingModelsCrossVersionTest
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.dom.DataStructuralEqualityKt
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.LanguageTreeToDomKt
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepResult
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver.AssignmentResolutionResult.Assigned
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.ParserKt
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent

@TargetGradleVersion(">=8.9")
@ToolingApiVersion('>=8.9')
class DeclarativeDslToolingModelsCrossVersionTest extends AbstractDeclarativeDslToolingModelsCrossVersionTest {

    def setup() {
        settingsFile.delete() //we are using a declarative settings file
    }

    def 'can obtain model containing project schema, even in the presence of errors in project scripts'() {
        given:
        file("settings.gradle.dcl") << """
            rootProject.name = "test"
            include(":a")
            include(":b")
        """

        file("a/build.gradle.dcl") << " !%@ unpassable crappy crap"

        file("b/build.gradle.dcl") << ""

        when:
        DeclarativeSchemaModel model = fetchSchemaModel(DeclarativeSchemaModel.class)

        then:
        model != null

        def schema = model.getProjectSchema()
        !schema.dataClassesByFqName.isEmpty()
    }

    def 'model is obtained without configuring the project'() {
        given:
        file("settings.gradle.dcl") << """
            rootProject.name = "test"
            include(":a")
        """

        file("a/build.gradle.dcl") << ""

        when:
        def listener = new ConfigurationPhaseMonitoringListener()
        DeclarativeSchemaModel model = fetchSchemaModel(DeclarativeSchemaModel.class, listener)

        then:
        model != null
        listener.hasSeenSomeEvents && listener.configPhaseStartEvents.isEmpty()
    }

    def 'schema contains custom software type from included build'() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.dcl") << ecosystemPluginInSettings

        file("build.gradle.dcl") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        DeclarativeSchemaModel model = fetchSchemaModel(DeclarativeSchemaModel.class)

        then:
        model != null

        def schema = model.getProjectSchema()
        def topLevelReceiverType = schema.topLevelReceiverType
        def topLevelFunctions = topLevelReceiverType.memberFunctions.collect { toString() }
        !topLevelFunctions.find { it.contains("simpleName=testSoftwareType") }
    }

    @TargetGradleVersion(">=8.10")
    def 'interpretation sequences obtained via TAPI are suitable for analysis'() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.dcl") << """
            $ecosystemPluginInSettings
            defaults {
                testSoftwareType {
                    id = "default"
                }
            }
        """

        file("build.gradle.dcl") << declarativeScriptThatConfiguresOnlyTestSoftwareTypeFoo

        when:
        DeclarativeSchemaModel model = fetchSchemaModel(DeclarativeSchemaModel.class)

        then:
        def evaluator = SimpleAnalysisEvaluator.@Companion.withSchema(model.settingsSequence, model.projectSequence)
        def settings = evaluator.evaluate("settings.gradle.dcl", file("settings.gradle.dcl").text)
        def project = evaluator.evaluate("build.gradle.dcl", file("build.gradle.dcl").text)

        ["settingsPluginManagement", "settingsPlugins", "settingsDefaults", "settings"].toSet() == settings.stepResults.keySet().collect { it.stepIdentifier.key }.toSet()
        ["project"].toSet() == project.stepResults.keySet().collect { it.stepIdentifier.key }.toSet()

        and: 'defaults get properly applied'
        // check the conventions in the resolution results, they should be there, and it is independent of the DOM overlay
        def projectEvaluated = project.stepResults.values()[0] as EvaluationResult.Evaluated<AnalysisStepResult>
        projectEvaluated.stepResult.assignmentTrace.resolvedAssignments.entrySet().any {
            it.key.property.name == "id" && ((it.value as Assigned).objectOrigin as ObjectOrigin.ConstantOrigin).literal.value == "default"
        }

        when: 'the build and settings files contain errors'
        def settingsWithErrors = evaluator.evaluate("settings.gradle.dcl", file("settings.gradle.dcl").text.replace("id", "unresolvedId"))
        def projectWithErrors = evaluator.evaluate("build.gradle.dcl", file("build.gradle.dcl").text + "\nunresolvedToTestErrorHandling()")

        then: 'the client can still produce a build file document with conventions applied from settings'
        documentIsEquivalentTo(
            """
            testSoftwareType {
                unresolvedId = "default"
                foo {
                    bar = "baz"
                }
            }
            unresolvedToTestErrorHandling()
            """,
            AnalysisDocumentUtils.INSTANCE.documentWithModelDefaults(settingsWithErrors, projectWithErrors).document
        )
    }

    static String getEcosystemPluginInSettings() {
        """
        pluginManagement {
            includeBuild("plugins")
        }
        plugins {
            id("com.example.test-software-type")
        }
        """.stripMargin()
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

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareTypeFoo() {
        return """
            testSoftwareType {
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
            import org.gradle.api.internal.plugins.software.SoftwareType;
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
            import org.gradle.api.internal.plugins.software.SoftwareType;
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
                    ((SettingsInternal)target).getServices().get(SoftwareTypeRegistry.class).register(SoftwareTypeImplPlugin.class, SoftwareTypeRegistrationPlugin.class);
                    ((SettingsInternal)target).getServices().get(SoftwareTypeRegistry.class).register(AnotherSoftwareTypeImplPlugin.class, SoftwareTypeRegistrationPlugin.class);
                }
            }
        """

        return pluginBuilder
    }

    private <T> T fetchSchemaModel(Class<T> modelType, ProgressListener listener = null) {
        toolingApi.withConnection({ connection ->
            def model = connection.model(modelType)
            if (listener != null) {
                model.addProgressListener(listener)
            }
            model.get()
        })
    }

    private static boolean documentIsEquivalentTo(
        String expectedDocumentText,
        def actualDocument // can't declare it as DeclarativeDocument, throws NCDFE from the test runner (???)
    ) {
        def doc = actualDocument as DeclarativeDocument
        def parsed = ParserKt.parse(expectedDocumentText)
        def languageTree = new DefaultLanguageTreeBuilder().build(
            parsed, new SourceIdentifier("test")
        )
        def expectedDocument = LanguageTreeToDomKt.toDocument(languageTree)
        DataStructuralEqualityKt.structurallyEqualsAsData(doc, expectedDocument)
    }

    private static final class ConfigurationPhaseMonitoringListener implements ProgressListener {

        boolean hasSeenSomeEvents = false
        final List<ProgressEvent> configPhaseStartEvents = new ArrayList<>()

        @Override
        void statusChanged(ProgressEvent event) {
            hasSeenSomeEvents = true
            if (event instanceof BuildPhaseStartEvent) {
                BuildPhaseStartEvent buildPhaseStartEvent = (BuildPhaseStartEvent) event
                if (buildPhaseStartEvent.descriptor.buildPhase.startsWith("CONFIGURE")) {
                    configPhaseStartEvents.add(event)
                }
            }
        }
    }
}
