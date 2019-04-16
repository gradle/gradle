/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import spock.lang.Specification

class LanguageLibraryProjectInitDescriptorSpec extends Specification {

    FileResolver fileResolver = Mock()
    TemplateOperationFactory templateOperationFactory = Mock()
    BuildScriptBuilderFactory scriptBuilderFactory = Mock()
    TemplateLibraryVersionProvider libraryVersionProvider = Mock()
    LanguageLibraryProjectInitDescriptor descriptor
    TemplateOperationFactory.TemplateOperationBuilder templateOperationBuilder = Mock(TemplateOperationFactory.TemplateOperationBuilder)

    def "generates from template within sourceSet"() {
        setup:
        descriptor = new TestLanguageLibraryProjectInitDescriptor(language, scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider)

        when:
        descriptor.fromSourceTemplate("someTemplate/SomeClazz.somelang.template", sourceSet)

        then:
        1 * templateOperationFactory.newTemplateOperation() >> templateOperationBuilder
        1 * templateOperationBuilder.withTemplate("someTemplate/SomeClazz.somelang.template") >> templateOperationBuilder
        1 * templateOperationBuilder.withTarget(target) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("packageDecl", "") >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("className", "") >> templateOperationBuilder
        1 * templateOperationBuilder.create() >> Mock(TemplateOperation)

        where:
        language        | sourceSet   | target
        "somelang"      | "main"      | "src/main/somelang/SomeClazz.somelang"
        "someotherlang" | "test"      | "src/test/someotherlang/SomeClazz.somelang"
        "somelang"      | "integTest" | "src/integTest/somelang/SomeClazz.somelang"
    }

    def "generates source file with package from template"() {
        setup:
        def settings = new InitSettings("project", BuildInitDsl.GROOVY, "my.lib", BuildInitTestFramework.NONE)
        descriptor = new TestLanguageLibraryProjectInitDescriptor(language, scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider)

        when:
        descriptor.fromSourceTemplate("someTemplate/SomeClazz.somelang.template", settings, sourceSet)

        then:
        1 * templateOperationFactory.newTemplateOperation() >> templateOperationBuilder
        1 * templateOperationBuilder.withTemplate("someTemplate/SomeClazz.somelang.template") >> templateOperationBuilder
        1 * templateOperationBuilder.withTarget(target) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("packageDecl", "package my.lib") >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("className", "") >> templateOperationBuilder
        1 * templateOperationBuilder.create() >> Mock(TemplateOperation)

        where:
        language        | sourceSet   | target
        "somelang"      | "main"      | "src/main/somelang/my/lib/SomeClazz.somelang"
        "someotherlang" | "test"      | "src/test/someotherlang/my/lib/SomeClazz.somelang"
        "somelang"      | "integTest" | "src/integTest/somelang/my/lib/SomeClazz.somelang"
    }

    def "can specify output class name"() {
        setup:
        def settings = new InitSettings("project", BuildInitDsl.GROOVY, packageName, BuildInitTestFramework.NONE)
        descriptor = new TestLanguageLibraryProjectInitDescriptor("somelang", scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider)

        when:
        descriptor.fromSourceTemplate("someTemplate/SomeClazz.somelang.template", settings) {
            it.className = className
        }

        then:
        1 * templateOperationFactory.newTemplateOperation() >> templateOperationBuilder
        1 * templateOperationBuilder.withTemplate("someTemplate/SomeClazz.somelang.template") >> templateOperationBuilder
        1 * templateOperationBuilder.withTarget(target) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("packageDecl", _) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("className", className) >> templateOperationBuilder
        1 * templateOperationBuilder.create() >> Mock(TemplateOperation)

        where:
        packageName | className | target
        ""          | "Main"    | "src/main/somelang/Main.somelang"
        "a.b"       | "Main"    | "src/main/somelang/a/b/Main.somelang"
    }

    def "whenNoSourcesAvailable creates template operation checking for sources"() {
        setup:
        def mainSourceDirectory = Mock(FileTreeInternal)
        def testSourceDirectory = Mock(FileTreeInternal)
        def delegate = Mock(TemplateOperation)
        descriptor = new TestLanguageLibraryProjectInitDescriptor("somelang", scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider)

        when:
        descriptor.whenNoSourcesAvailable(delegate).generate()

        then:
        1 * mainSourceDirectory.empty >> noMainSources
        _ * testSourceDirectory.empty >> noTestSources
        1 * fileResolver.resolveFilesAsTree("src/main/somelang") >> mainSourceDirectory
        _ * fileResolver.resolveFilesAsTree("src/test/somelang") >> testSourceDirectory
        delegateInvocation * delegate.generate()

        where:
        noMainSources | noTestSources | delegateInvocation
        true          | true          | 1
        true          | false         | 1
        false         | false         | 0
    }

    class TestLanguageLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
        Language language

        TestLanguageLibraryProjectInitDescriptor(String language, BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider) {
            super(scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider)
            this.language = new Language(language)
        }

        @Override
        boolean supportsPackage() {
            return true
        }

        @Override
        String getId() {
            return "test"
        }

        @Override
        void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
            throw new UnsupportedOperationException()
        }

        @Override
        Set<BuildInitTestFramework> getTestFrameworks() {
            return []
        }

        @Override
        BuildInitTestFramework getDefaultTestFramework() {
            return BuildInitTestFramework.NONE
        }
    }
}
