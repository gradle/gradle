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

import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.AbstractBuildableModelElement
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.LanguageSourceSetFactory
import org.gradle.language.base.plugins.LanguageBasePlugin
import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.core.ModelAction
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelReference
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

    LanguageTypeModelRuleExtractor ruleHandler = new LanguageTypeModelRuleExtractor(schemaStore)

    @Override
    Class<? extends Annotation> getAnnotation() {
        return LanguageType
    }

    @Unroll
    def "decent error message for rule declaration problem - #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod.method)

        when:
        extract(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == """Type ${ruleClass.name} is not a valid rule source:
- Method ${ruleDescription} is not a valid rule method: ${expectedMessage}"""

        where:
        methodName                    | expectedMessage                                                                                           | descr
        "returnValue"                 | "A method annotated with @LanguageType must have void return type."                                       | "non void method"
        "noParams"                    | "A method annotated with @LanguageType must have a single parameter of type ${LanguageTypeBuilder.name}." | "no LanguageTypeBuilder subject"
        "wrongSubject"                | "A method annotated with @LanguageType must have a single parameter of type ${LanguageTypeBuilder.name}." | "wrong rule subject type"
        "rawLanguageTypeBuilder"      | "Parameter of type org.gradle.platform.base.LanguageTypeBuilder must declare a type parameter."           | "non typed ModelMap parameter"
        "wildcardLanguageTypeBuilder" | "Language type '?' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."                  | "wild card ModelMap parameter"
        "wrongSubType"                | "Language type 'java.lang.String' is not a subtype of 'org.gradle.language.base.LanguageSourceSet'."      | "public type not extending LanguageSourceSet"
    }

    @Unroll
    def "decent error message for rule behaviour problem - #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        apply(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid language model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                          | expectedMessage                                                                                                          | descr
        "notImplementingLibraryType"        | "Language implementation ${NotImplementingCustomLanguageSourceSet.name} must implement ${CustomLanguageSourceSet.name}." | "implementation not implementing type class"
        "notExtendingBaseLanguageSourceSet" | "Language implementation ${NotExtendingBaseLanguageSourceSet.name} must extend ${BaseLanguageSourceSet.name}."           | "implementation not extending ${BaseLanguageSourceSet.name}"
        "noPublicCtorImplementation"        | "Language implementation ${ImplementationWithNoPublicConstructor.name} must have public default constructor."            | "implementation with not public default constructor"
    }

    def "applies LanguageBasePlugin and creates language type rule"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def registration = extract(ruleDefinitionForMethod("validTypeRule"))

        then:
        registration.ruleDependencies == [LanguageBasePlugin]

        when:
        apply(registration, mockRegistry)

        then:
        1 * mockRegistry.configure(_, _) >> { ModelActionRole role, ModelAction action ->
            assert role == ModelActionRole.Mutate
            assert action.subject == ModelReference.of(LanguageSourceSetFactory)
        }
        0 * _
    }

    static interface CustomLanguageSourceSet extends LanguageSourceSet {}

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
        void generatedBy(Task generatorTask) {
        }

        @Override
        String getParentName() {
            return null
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
        void noPublicCtorImplementation(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
            languageBuilder.defaultImplementation(ImplementationWithNoPublicConstructor)
        }

        @LanguageType
        void noLanguageName(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
            languageBuilder.defaultImplementation(ImplementingCustomLanguageSourceSet)
        }

        @LanguageType
        void validTypeRule(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
            languageBuilder.defaultImplementation(ImplementingCustomLanguageSourceSet)
        }
    }
}
