/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.registry
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.RuleSourceDependencies
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.BinaryTasks
import org.gradle.platform.base.InvalidModelException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Unroll

import java.lang.annotation.Annotation
import java.lang.reflect.Method

class BinaryTasksRuleDefinitionHandlerTest extends AbstractAnnotationRuleDefinitionHandlerTest {

    def ruleDependencies = Mock(RuleSourceDependencies)

    BinaryTasksRuleDefinitionHandler ruleHandler = new BinaryTasksRuleDefinitionHandler()

    @Override
    Class<? extends Annotation> getAnnotation() {
        return BinaryTasks
    }

    def ruleDefinitionForMethod(String methodName) {
        for (Method candidate : Rules.class.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                return DefaultMethodRuleDefinition.create(Rules.class, candidate)
            }
        }
        throw new IllegalArgumentException("Not a test method name")
    }

    @Unroll
    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        ruleHandler.register(ruleMethod, modelRegistry, ruleDependencies)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid BinaryTask model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName               | expectedMessage                                                                                             | descr
        "returnValue"            | "Method annotated with @BinaryTasks must not have a return value."                                                           | "non void method"
        "noParams"               | "Method annotated with @BinaryTasks must have a parameter of type '${CollectionBuilder.name}'."                              | "no CollectionBuilder subject"
        "wrongSubject"           | "Method annotated with @BinaryTasks first parameter must be of type '${CollectionBuilder.name}'."                            | "wrong rule subject type"
        "noBinaryParameter"      | "Method annotated with @BinaryTasks must have one parameter extending BinarySpec. Found no parameter extending BinarySpec."  | "no component spec parameter"
        "rawCollectionBuilder"   | "Parameter of type 'CollectionBuilder' must declare a type parameter extending 'Task'."                     | "non typed CollectionBuilder parameter"
    }

    @Unroll
    def "applies ComponentModelBasePlugin and adds binary task creation rule #descr"() {
        when:
        ruleHandler.register(ruleDefinitionForMethod(ruleName), modelRegistry, ruleDependencies)

        then:
        1 * ruleDependencies.add(ComponentModelBasePlugin)

        and:
        1 * modelRegistry.mutate(_)

        where:
        ruleName          |  descr
        "validTypeRule"   |  "for plain sample binary"
    }

    def getStringDescription(MethodRuleDefinition ruleDefinition) {
        def builder = new StringBuilder()
        ruleDefinition.descriptor.describeTo(builder)
        builder.toString()
    }

    def aProjectPlugin() {
        ruleDependencies = ProjectBuilder.builder().build()
        _ * pluginApplication.target >> ruleDependencies
    }

    def aSettingsPlugin(def plugin) {
        Settings settings = Mock(Settings)
        _ * pluginApplication.target >> settings
        _ * pluginApplication.plugin >> plugin
        ruleHandler = new ComponentTypeRuleDefinitionHandler(instantiator)
    }

    interface SomeBinary extends BinarySpec {}

    static class Rules {

        @BinaryTasks
        static String returnValue(CollectionBuilder<Task> builder, SomeBinary binary) {
        }

        @BinaryTasks
        static void noParams() {
        }

        @BinaryTasks
        static void wrongSubject(binary) {
        }

        @BinaryTasks
        static void rawCollectionBuilder(CollectionBuilder tasks, SomeBinary binary) {
        }

        @BinaryTasks
        static void noBinaryParameter(CollectionBuilder<Task> builder) {
        }

        @BinaryTasks
        static void validTypeRule(CollectionBuilder<Task> tasks, SomeBinary binary) {
            tasks.create("create${binary.getName()}")
        }
    }
}