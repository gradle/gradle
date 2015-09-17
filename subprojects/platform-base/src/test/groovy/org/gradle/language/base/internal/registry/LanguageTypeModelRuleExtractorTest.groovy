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

package org.gradle.language.base.internal.registry
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.AbstractBuildableModelElement
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.testinterfaces.CustomLanguageSourceSet
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.core.*
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.InvalidModelException
import org.gradle.platform.base.LanguageType
import org.gradle.platform.base.LanguageTypeBuilder
import org.gradle.platform.base.internal.registry.AbstractAnnotationModelRuleExtractorTest
import org.gradle.platform.base.internal.registry.LanguageTypeModelRuleExtractor
import spock.lang.Unroll

import java.lang.annotation.Annotation

class LanguageTypeModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {

    Class<?> ruleClass = Rules

    LanguageTypeModelRuleExtractor ruleHandler = new LanguageTypeModelRuleExtractor(DefaultModelSchemaStore.getInstance())

    @Override
    Class<? extends Annotation> getAnnotation() {
        return LanguageType
    }

    @Unroll
    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        ruleHandler.registration(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid language model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                          | expectedMessage                                                                                                              | descr
        "returnValue"                       | "Method annotated with @LanguageType must not have a return value."                                                          | "non void method"
        "noParams"                          | "Method annotated with @LanguageType must have a single parameter of type '${LanguageTypeBuilder.name}'."                    | "no LanguageTypeBuilder subject"
        "wrongSubject"                      | "Method annotated with @LanguageType must have a single parameter of type '${LanguageTypeBuilder.name}'."                    | "wrong rule subject type"
        "rawLanguageTypeBuilder"            | "Parameter of type 'org.gradle.platform.base.LanguageTypeBuilder' must declare a type parameter."                            | "non typed ModelMap parameter"
        "wildcardLanguageTypeBuilder"       | "Language type '?' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."                                     | "wild card ModelMap parameter"
        "notImplementingLibraryType"        | "Language implementation '${NotImplementingCustomLanguageSourceSet.name}' must implement '${CustomLanguageSourceSet.name}'." | "implementation not implementing type class"
        "wrongSubType"                      | "Language type 'java.lang.String' is not a subtype of 'org.gradle.language.base.LanguageSourceSet'."                         | "implementation not extending BaseComponentSpec"
        "notExtendingBaseLanguageSourceSet" | "Language implementation '${NotExtendingBaseLanguageSourceSet.name}' must extend '${BaseLanguageSourceSet.name}'."           | "implementation not extending ${BaseLanguageSourceSet.name}"
        "noPublicCtorImplementation"        | "Language implementation '${ImplementationWithNoPublicConstructor.name}' must have public default constructor."              | "implementation with not public default constructor"
    }

    def "applies ComponentModelBasePlugin and creates language type rule"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod("validTypeRule"))

        then:
        registration instanceof ExtractedModelAction
        registration.ruleDependencies == [ComponentModelBasePlugin]

        when:
        registration.apply(mockRegistry, ModelPath.ROOT)

        then:
        1 * mockRegistry.configure(_, _, _) >> { ModelActionRole role, ModelAction<?> action, ModelPath scope ->
            assert role == ModelActionRole.Defaults
            assert action.subject == ModelReference.of(LanguageRegistry)
        }
        0 * _
    }

    def "only applies ComponentModelBasePlugin when implementation not set"() {
        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod("noImplementationTypeRule"))

        then:
        registration instanceof DependencyOnlyExtractedModelRule
        registration.ruleDependencies == [ComponentModelBasePlugin]
    }

    static class ImplementingCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {
    }

    static class ImplementationWithNoPublicConstructor extends BaseLanguageSourceSet implements CustomLanguageSourceSet {
        public ImplementationWithNoPublicConstructor(String someString) {}
    }

    class NotImplementingCustomLanguageSourceSet extends BaseLanguageSourceSet {}

    class NotExtendingBaseLanguageSourceSet extends AbstractBuildableModelElement implements CustomLanguageSourceSet {
        @Override
        String getDisplayName() {
            return null
        }

        @Override
        SourceDirectorySet getSource() {
            return null
        }

        @Override
        void source(Action<? super SourceDirectorySet> config) {
        }

        @Override
        void generatedBy(Task generatorTask) {
        }

        @Override
        String getName() {
            return null
        }
    }

    static class Rules {

        @LanguageType
        String returnValue(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
            return null
        }

        @LanguageType
        void noParams() {
        }

        @LanguageType
        void wrongSubject(LanguageSourceSet sourcet) {
        }

        @LanguageType
        void rawLanguageTypeBuilder(LanguageTypeBuilder builder) {
        }

        @LanguageType
        void wildcardLanguageTypeBuilder(LanguageTypeBuilder<?> builder) {
        }

        @LanguageType
        void wrongSubType(LanguageTypeBuilder<String> languageBuilder) {
        }

        @LanguageType
        void notExtendingBaseLanguageSourceSet(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
            languageBuilder.defaultImplementation(NotExtendingBaseLanguageSourceSet)
        }

        @LanguageType
        void notImplementingLibraryType(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
            languageBuilder.defaultImplementation(NotImplementingCustomLanguageSourceSet)
        }

        @LanguageType
        void noImplementationTypeRule(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
        }

        @LanguageType
        void noPublicCtorImplementation(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
            languageBuilder.defaultImplementation(ImplementationWithNoPublicConstructor)
        }

        @LanguageType
        void validTypeRule(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
            languageBuilder.defaultImplementation(ImplementingCustomLanguageSourceSet)
        }
    }
}
