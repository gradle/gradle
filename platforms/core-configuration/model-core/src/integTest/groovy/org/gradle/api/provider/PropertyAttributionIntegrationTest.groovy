/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects

@UnsupportedWithConfigurationCache(because = 'S2 attribution is not transported through configuration cache')
@ToBeFixedForIsolatedProjects(because = 'Fixtures deliberately configure properties across project boundaries')
class PropertyAttributionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument('-Dorg.gradle.internal.property-provenance=true')
    }

    def 'effective replace provenance retains plugin occurrences through copy and finalization'() {
        given:
        buildFile << """
            class SourcePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def value = project.objects.property(String)
                    project.extensions.add('tracked', value)
                    value.convention('root')
                }
            }
            class UpdatePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tracked.replace { previous -> previous.map { it + '-one' }.map { it + '-two' } }
                    project.tracked.replace { previous -> previous.map { it + '-three' } }
                }
            }
            apply plugin: SourcePlugin
            apply plugin: UpdatePlugin
            def copy = tracked.shallowCopy()
            def before = tracked.effectiveProvenance
            assert before.source.occurrence.attribution.contributor.identity == 'SourcePlugin'
            assert before.updates.inApplicationOrder()*.attribution*.contributor*.identity == ['UpdatePlugin', 'UpdatePlugin']
            assert before.updates.inApplicationOrder()[0] != before.updates.inApplicationOrder()[1]
            assert before.updates.inApplicationOrder()[0].operation.shapes.size() == 2
            tracked.convention('later')
            tracked.finalizeValue()
            assert tracked.get() == 'root-one-two-three'
            assert copy.get() == tracked.get()
            assert tracked.effectiveProvenance.source.occurrence.is(before.source.occurrence)
            assert tracked.effectiveProvenance.updates.is(before.updates)
            assert copy.effectiveProvenance.updates.is(before.updates)
            println 'effective provenance verified'
        """

        when:
        succeeds('help')

        then:
        outputContains('effective provenance verified')
    }

    def 'plugin ID class fallback nested application and deferred registrants reach accepted mutations'() {
        given:
        pluginBuild('buildSrc')
        file('buildSrc/src/main/java/example/Outer.java') << '''
            package example;
            import org.gradle.api.*;
            import org.gradle.api.provider.Property;
            public class Outer implements Plugin<Project> {
                public void apply(Project project) {
                    Property<String> value = project.getObjects().property(String.class);
                    project.getExtensions().add("tracked", value);
                    value.set("outer");
                    Inspect.print("outer", value);
                    project.getPluginManager().apply(Inner.class);
                    value.set("restored");
                    Inspect.print("restored", value);
                    project.getPluginManager().withPlugin("java", ignored -> {
                        value.set("withPlugin");
                        Inspect.print("withPlugin", value);
                    });
                    project.afterEvaluate(ignored -> {
                        value.set("afterEvaluate");
                        Inspect.print("afterEvaluate", value);
                        project.getTasks().named("nested").configure(nested -> {
                            value.set("nested");
                            Inspect.print("nested", value);
                        });
                    });
                    project.getTasks().register("later", task -> {
                        value.set("deferred");
                        Inspect.print("deferred", value);
                    });
                    project.getTasks().register("nested");
                }
            }
        '''
        file('buildSrc/src/main/java/example/Inner.java') << '''
            package example;
            import org.gradle.api.*;
            import org.gradle.api.provider.Property;
            public class Inner implements Plugin<Project> {
                public void apply(Project project) {
                    Property<String> value = (Property<String>) project.getExtensions().getByName("tracked");
                    value.set("inner");
                    Inspect.print("inner", value);
                }
            }
        '''
        buildFile << '''
            plugins { id 'example.outer' }
            apply plugin: 'java'
            tasks.named('later') {
                tracked.set('script')
                example.Inspect.print('script', tracked)
            }
        '''

        when:
        succeeds('later', 'nested')

        then:
        ['outer', 'restored', 'withPlugin', 'afterEvaluate', 'deferred', 'nested'].each {
            outputContains("$it|PLUGIN_ID|example.outer|:|:|PLUGIN_ID")
        }
        outputContains('inner|PLUGIN_CLASS|example.Inner|:|:|PLUGIN_CLASS')
        outputContains('script|BUILD_AUTHOR||:|:|PROJECT_SCRIPT')
        !output.contains('Failure trace to source')
    }

    def 'root and sibling script bindings keep source scope separate from property ownership'() {
        given:
        settingsFile << "include 'app', 'sibling'"
        buildFile << '''
            def value = project(':app').objects.property(String)
            project(':app').extensions.add('tracked', value)
            value.set('root')
            println "root|${value.lastAcceptedMutation.attribution.sourceScope.scopePath}|${value.provenanceTarget.owner.scopePath}|${value.lastAcceptedMutation.attribution.origin.kind}"
            ext.author = value.lastAcceptedMutation.attribution.contributor
        '''
        file('app/build.gradle') << ''
        file('sibling/build.gradle') << '''
            def value = project(':app').tracked
            value.set('sibling')
            println "sibling|${value.lastAcceptedMutation.attribution.sourceScope.scopePath}|${value.provenanceTarget.owner.scopePath}|${value.lastAcceptedMutation.attribution.origin.kind}"
            println "same-author|${value.lastAcceptedMutation.attribution.contributor == rootProject.author}"
        '''

        when:
        succeeds('help')

        then:
        outputContains('root|:|:app|PROJECT_SCRIPT')
        outputContains('sibling|:sibling|:app|PROJECT_SCRIPT')
        outputContains('same-author|true')
    }

    def 'applied script named settings gradle is an applied contributor and Kotlin top level is build author'() {
        given:
        file('build.gradle.kts') << '''
            import org.gradle.api.internal.provider.AttributedProperty
            val value = objects.property(String::class.java) as AttributedProperty<String>
            extensions.add("tracked", value)
            value.set("kotlin")
            println("kotlin|" + value.lastAcceptedMutation!!.attribution.origin.kind)
            apply(from = "scripts/settings.gradle")
            value.set("restored")
            println("restored|" + value.lastAcceptedMutation!!.attribution.origin.kind)
        '''
        file('scripts/settings.gradle') << '''
            tracked.set('applied')
            println "applied|${tracked.lastAcceptedMutation.attribution.origin.kind}|${tracked.lastAcceptedMutation.attribution.contributor.kind}"
        '''

        when:
        succeeds('help')

        then:
        outputContains('kotlin|PROJECT_SCRIPT')
        outputContains('applied|APPLIED_SCRIPT|APPLIED_SCRIPT')
        outputContains('restored|PROJECT_SCRIPT')
    }

    def 'settings plugin attribution reaches project properties through #boundary'() {
        given:
        pluginBuild('build-logic', true)
        settingsFile << '''
            pluginManagement { includeBuild('build-logic') }
            plugins { id 'example.settings' }
            include 'app'
        '''
        file('app/build.gradle') << ''
        file('build-logic/src/main/java/example/SettingsOrigin.java') << """
            package example;
            import org.gradle.api.*;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;
            import javax.inject.Inject;
            public class SettingsOrigin implements Plugin<Settings> {
                private final ObjectFactory objects;
                @Inject public SettingsOrigin(ObjectFactory objects) { this.objects = objects; }
                public void apply(Settings settings) {
                    Property<String> own = objects.property(String.class);
                    own.set("settings owned");
                    System.out.println("settings-owned|" + own.getClass().getSimpleName());
                    settings.getGradle().${isolated ? 'getLifecycle().' : ''}beforeProject(project -> {
                        Property<String> value = project.getObjects().property(String.class);
                        ${nested ? '' : 'value.set("direct"); Inspect.print("settings-origin", value);'}
                        project.getTasks().register("inspect", task -> {
                            ${nested ? 'value.set("nested"); Inspect.print("settings-origin", value);' : ''}
                        });
                    });
                }
            }
        """

        when:
        succeeds(':app:inspect')

        then:
        outputContains('settings-origin|PLUGIN_ID|example.settings|settings|:app|PLUGIN_ID')
        outputContains('settings-owned|DefaultProperty')

        where:
        boundary                   | nested | isolated
        'beforeProject'            | false  | false
        'nested callback'          | true   | false
        'lifecycle.beforeProject'  | false  | true
        'lifecycle nested callback'| true   | true
    }

    def 'settings script configuring project property retains settings role'() {
        given:
        settingsFile << '''
            gradle.beforeProject { project ->
                def value = project.objects.property(String)
                value.set('settings script')
                println "settings-script|${value.lastAcceptedMutation.attribution.origin.kind}|${value.lastAcceptedMutation.attribution.sourceScope.scopePath}|${value.provenanceTarget.owner.scopePath}"
            }
        '''

        when:
        succeeds('help')

        then:
        outputContains('settings-script|SETTINGS_SCRIPT|settings|:')
    }

    def 'managed extension and task scalar factories use project attribution without tracking file properties'() {
        given:
        buildFile << '''
            abstract class Message {
                abstract Property<String> getValue()
            }
            abstract class Show extends DefaultTask {
                @Internal abstract Property<String> getValue()
            }
            def message = extensions.create('message', Message)
            message.value.set('extension')
            println "extension|${message.value.lastAcceptedMutation.attribution.origin.kind}"
            println "enabled-file|${objects.fileProperty().class.simpleName}"
            tasks.register('show', Show) {
                value.set('task')
                println "task|${value.lastAcceptedMutation.attribution.origin.kind}"
            }
        '''

        when:
        succeeds('show')

        then:
        outputContains('extension|PROJECT_SCRIPT')
        outputContains('task|PROJECT_SCRIPT')
        outputContains('enabled-file|DefaultRegularFileVar')
    }

    def 'disabled scalar and enabled collection factories preserve ordinary classes and messages'() {
        given:
        buildFile << '''
            def value = objects.property(String)
            println "scalar|${value.class.simpleName}"
            println "list|${objects.listProperty(String).class.simpleName}"
            println "file|${objects.fileProperty().class.simpleName}"
            value.set('present')
            value.finalizeValue()
            value.set('rejected')
        '''
        executer.withArgument('-Dorg.gradle.internal.property-provenance=false')

        when:
        fails('help')

        then:
        outputContains('scalar|DefaultProperty')
        outputContains('list|DefaultListProperty')
        outputContains('file|DefaultRegularFileVar')
        failure.assertHasCause('The value for this property is final and cannot be changed any further.')
        !failure.error.contains('Failure trace to source')
    }

    private void pluginBuild(String directory, boolean settingsPlugin = false) {
        file("$directory/settings.gradle") << 'rootProject.name = "test-logic"'
        file("$directory/build.gradle") << """
            plugins { id 'java-gradle-plugin' }
            gradlePlugin {
                plugins {
                    sample {
                        id = '${settingsPlugin ? 'example.settings' : 'example.outer'}'
                        implementationClass = '${settingsPlugin ? 'example.SettingsOrigin' : 'example.Outer'}'
                    }
                }
            }
        """
        file("$directory/src/main/java/example/Inspect.java") << '''
            package example;
            import org.gradle.api.provider.Property;
            import org.gradle.api.internal.provider.AttributedProperty;
            import org.gradle.api.internal.provenance.Attribution;
            public class Inspect {
                public static void print(String label, Property<String> property) {
                    AttributedProperty<String> tracked = (AttributedProperty<String>) property;
                    Attribution attribution = tracked.getLastAcceptedMutation().getAttribution();
                    System.out.println(label + "|" + attribution.getContributor().getKind() + "|" + attribution.getContributor().getIdentity()
                        + "|" + attribution.getSourceScope().getScopePath() + "|" + tracked.getProvenanceTarget().getOwner().getScopePath()
                        + "|" + attribution.getOrigin().getKind());
                }
            }
        '''
    }
}
