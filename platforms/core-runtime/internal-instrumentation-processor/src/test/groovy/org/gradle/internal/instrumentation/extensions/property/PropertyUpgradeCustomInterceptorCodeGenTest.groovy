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

package org.gradle.internal.instrumentation.extensions.property

import com.google.testing.compile.Compilation
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.provider.views.ListPropertyListView
import org.gradle.api.internal.provider.views.MapPropertyMapView
import org.gradle.api.internal.provider.views.SetPropertySetView
import org.gradle.internal.instrumentation.InstrumentationCodeGenTest

import static com.google.testing.compile.CompilationSubject.assertThat
import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES

class PropertyUpgradeCustomInterceptorCodeGenTest extends InstrumentationCodeGenTest {

    def "should generate adapter for upgraded property with custom interception"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
            import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation;
            import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

            public abstract class Task {
                @ReplacesEagerProperty(adapter = Task.TaskAdapter.class)
                public abstract Property<Integer> getMaxErrors();

                static class TaskAdapter {
                    @BytecodeUpgrade
                    static int maxErrors(Task task) {
                        return 0;
                    }

                    @BytecodeUpgrade
                    static int getMaxErrors(Task task) {
                        return 0;
                    }

                    @BytecodeUpgrade
                    static Task maxErrors(Task task, int maxErrors) {
                        return task;
                    }

                    @BytecodeUpgrade
                    static void setMaxErrors(Task task, int maxErrors) {
                    }
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
             package org.gradle.test;
             import org.gradle.api.Generated;

             @Generated
             public class \$\$BridgeFor\$\$Task\$\$TaskAdapter {
                 public static int access_get_getMaxErrors(Task task) {
                     ${getDefaultPropertyUpgradeDeprecation("Task", "maxErrors")}
                     return Task.TaskAdapter.getMaxErrors(task);
                 }
                 public static int access_get_maxErrors(Task task) {
                     ${getDefaultPropertyUpgradeDeprecation("Task", "maxErrors")}
                     return Task.TaskAdapter.maxErrors(task);
                 }
                 public static Task access_set_maxErrors(Task task, int maxErrors) {
                     ${getDefaultPropertyUpgradeDeprecation("Task", "maxErrors")}
                     return Task.TaskAdapter.maxErrors(task, maxErrors);
                 }
                 public static void access_set_setMaxErrors(Task task, int maxErrors) {
                     ${getDefaultPropertyUpgradeDeprecation("Task", "maxErrors")}
                     Task.TaskAdapter.setMaxErrors(task, maxErrors);
                 }
             }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(generatedClass))
            .containsElementsIn(generatedClass)
    }
}
