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

package org.gradle.configurationcache

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

class ConfigurationCacheConventionMappingIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "doesn't restore convention value to incompatible field type"() {
        given:
        buildFile << """
            abstract class MyTask extends org.gradle.api.internal.ConventionTask {
                private final Property<String> archiveName = project.objects.property(String)
                @Input Property<String> getArchiveFileName() { return this.archiveName }

                @Internal String getArchiveName() { return archiveFileName.getOrNull() }
                void setArchiveName(String value) { archiveFileName.set(value) }

                @TaskAction
                void doIt() {
                    assert archiveFileName.get() == "something"
                }
            }

            task myTask(type: MyTask) {
                conventionMapping('archiveName') { 'not something' }
                archiveFileName.convention("something")
            }
        """

        expect: 'convention mapping is ignored'
        configurationCacheRun 'myTask'

        and: 'convention mapping is ignored just the same'
        configurationCacheRun 'myTask'
    }

    def "restores convention mapped task input property explicitly set to null"() {
        given:
        withConventionMappingForPropertyOfType String, '"42"'
        buildFile << '''
            tasks.named("ok") {
                inputProperty = null
            }
        '''

        when:
        configurationCacheRun 'ok'
        configurationCacheRun 'ok'

        then:
        outputContains 'this.value = null'
    }

    def "restores convention mapped task input property named after field with value of type #typeName"() {
        given:
        withConventionMappingForPropertyOfType type, value

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("this.value = ${output}")

        where:
        type      | value     | output
        String    | "'value'" | "value"
        Boolean   | "true"    | "true"
        boolean   | "true"    | "true"
        Character | "'a'"     | "a"
        char      | "'a'"     | "a"
        Byte      | "12"      | "12"
//        byte| "12"      | "12" // TODO: currently not working
        Short     | "12"      | "12"
        short     | "12"      | "12"
        Integer   | "12"      | "12"
        int       | "12"      | "12"
        Long      | "12"      | "12"
        long      | "12"      | "12"
        Float     | "12.1"    | "12.1"
        float     | "12.1"    | "12.1"
        Double    | "12.1"    | "12.1"
        double    | "12.1"    | "12.1"
        typeName = type.name
    }

    void withConventionMappingForPropertyOfType(Class type, String value) {
        final String typeName = type.name
        file('buildSrc/src/main/java/my/ConventionPlugin.java') << """
            package my;
            public class ConventionPlugin implements ${Plugin.name}<${Project.name}> {
                @Override
                public void apply(${Project.name} project) {
                    final Extension ext = project.getExtensions().create("conventions", Extension.class);
                    project.getTasks().withType(SomeTask.class).configureEach(task -> {
                        task.getConventionMapping().map("inputProperty", ext::getInputProperty);
                    });
                    project.getTasks().register("ok", SomeTask.class, task -> {
                    });
                }

                public static abstract class Extension {
                    private $typeName value;
                    public $typeName getInputProperty() { return value; }
                    public void setInputProperty($typeName value) { this.value = value; }
                }

                public static abstract class SomeTask extends ${ConventionTask.name} {
                    // Configuration cache only supports convention mapping for fields with matching names.
                    private $typeName inputProperty;
                    ${type.primitive ? '' : "@${Optional.name}"}
                    @${Input.name}
                    public $typeName getInputProperty() { return inputProperty; }
                    public void setInputProperty($typeName value) { this.inputProperty = value; }
                    @${TaskAction.name}
                    void run() {
                        System.out.println("this.value = " + getInputProperty());
                    }
                }
            }
        """
        buildFile """
            apply plugin: my.ConventionPlugin
            conventions {
                inputProperty = $value
            }
        """
    }
}
