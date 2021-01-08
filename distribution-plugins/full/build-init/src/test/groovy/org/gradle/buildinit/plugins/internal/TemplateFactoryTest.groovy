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

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import spock.lang.Specification

class TemplateFactoryTest extends Specification {

    Directory targetDir = Mock()
    RegularFile targetFile = Mock()
    TemplateOperationFactory templateOperationFactory = Mock()
    TemplateOperationFactory.TemplateOperationBuilder templateOperationBuilder = Mock(TemplateOperationFactory.TemplateOperationBuilder)

    def "generates from template within sourceSet"() {
        setup:
        def targetAsFile = new File(target)
        def settings = new InitSettings("project", ["app"], ModularizationOption.SINGLE_PROJECT, BuildInitDsl.GROOVY, "", BuildInitTestFramework.NONE, targetDir)
        def factory = new TemplateFactory(settings, Language.withName(language), templateOperationFactory)

        when:
        factory.fromSourceTemplate("someTemplate/SomeClazz.somelang.template", sourceSet)

        then:
        1 * targetDir.file(target) >> targetFile
        1 * targetFile.asFile >> targetAsFile
        1 * templateOperationFactory.newTemplateOperation() >> templateOperationBuilder
        1 * templateOperationBuilder.withTemplate("someTemplate/SomeClazz.somelang.template") >> templateOperationBuilder
        1 * templateOperationBuilder.withTarget(targetAsFile) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("basePackageName", "") >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("packageDecl", "") >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("className", "") >> templateOperationBuilder
        1 * templateOperationBuilder.create() >> Mock(TemplateOperation)

        where:
        language        | sourceSet   | target
        "somelang"      | "main"      | "app/src/main/somelang/SomeClazz.somelang"
        "someotherlang" | "test"      | "app/src/test/someotherlang/SomeClazz.somelang"
        "somelang"      | "integTest" | "app/src/integTest/somelang/SomeClazz.somelang"
    }

    def "generates source file with package from template"() {
        setup:
        def targetAsFile = new File(target)
        def settings = new InitSettings("project", ["app"], ModularizationOption.SINGLE_PROJECT, BuildInitDsl.GROOVY, "my.lib", BuildInitTestFramework.NONE, targetDir)
        def factory = new TemplateFactory(settings, Language.withName(language), templateOperationFactory)

        when:
        factory.fromSourceTemplate("someTemplate/SomeClazz.somelang.template", sourceSet)

        then:
        1 * targetDir.file(target) >> targetFile
        1 * targetFile.asFile >> targetAsFile
        1 * templateOperationFactory.newTemplateOperation() >> templateOperationBuilder
        1 * templateOperationBuilder.withTemplate("someTemplate/SomeClazz.somelang.template") >> templateOperationBuilder
        1 * templateOperationBuilder.withTarget(targetAsFile) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("basePackageName", "my.lib") >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("packageDecl", "package my.lib") >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("className", "") >> templateOperationBuilder
        1 * templateOperationBuilder.create() >> Mock(TemplateOperation)

        where:
        language        | sourceSet   | target
        "somelang"      | "main"      | "app/src/main/somelang/my/lib/SomeClazz.somelang"
        "someotherlang" | "test"      | "app/src/test/someotherlang/my/lib/SomeClazz.somelang"
        "somelang"      | "integTest" | "app/src/integTest/somelang/my/lib/SomeClazz.somelang"
    }

    def "can specify output class name"() {
        setup:
        def targetAsFile = new File(target)
        def settings = new InitSettings("project", ["app"], ModularizationOption.SINGLE_PROJECT, BuildInitDsl.GROOVY, packageName, BuildInitTestFramework.NONE, targetDir)
        def factory = new TemplateFactory(settings, Language.withName("somelang"), templateOperationFactory)

        when:
        factory.fromSourceTemplate("someTemplate/SomeClazz.somelang.template") {
            it.subproject("app")
            it.className(className)
        }

        then:
        1 * targetDir.file(target) >> targetFile
        1 * targetFile.asFile >> targetAsFile
        1 * templateOperationFactory.newTemplateOperation() >> templateOperationBuilder
        1 * templateOperationBuilder.withTemplate("someTemplate/SomeClazz.somelang.template") >> templateOperationBuilder
        1 * templateOperationBuilder.withTarget(targetAsFile) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("basePackageName", _) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("packageDecl", _) >> templateOperationBuilder
        1 * templateOperationBuilder.withBinding("className", className) >> templateOperationBuilder
        1 * templateOperationBuilder.create() >> Mock(TemplateOperation)

        where:
        packageName | className | target
        ""          | "Main"    | "app/src/main/somelang/Main.somelang"
        "a.b"       | "Main"    | "app/src/main/somelang/a/b/Main.somelang"
    }

    def "whenNoSourcesAvailable creates template operation checking for sources"() {
        setup:
        def mainSourceDirectory = Mock(Directory)
        def testSourceDirectory = Mock(Directory)
        def mainSourceFileTree = Mock(FileTreeInternal)
        def testSourceFileTree  = Mock(FileTreeInternal)
        def delegate = Mock(TemplateOperation)
        def settings = new InitSettings("project", ["app"], ModularizationOption.SINGLE_PROJECT, BuildInitDsl.GROOVY, "my.lib", BuildInitTestFramework.NONE, targetDir)
        def factory = new TemplateFactory(settings, Language.withName("somelang"), templateOperationFactory)

        when:
        factory.whenNoSourcesAvailable(delegate).generate()

        then:
        1 * targetDir.dir("app/src/main/somelang") >> mainSourceDirectory
        1 * targetDir.dir("app/src/test/somelang") >> testSourceDirectory
        1 * mainSourceDirectory.asFileTree >> mainSourceFileTree
        1 * testSourceDirectory.asFileTree >> testSourceFileTree
        1 * mainSourceFileTree.empty >> noMainSources
        _ * testSourceFileTree.empty >> noTestSources
        delegateInvocation * delegate.generate()

        where:
        noMainSources | noTestSources | delegateInvocation
        true          | true          | 1
        true          | false         | 1
        false         | false         | 0
    }
}
