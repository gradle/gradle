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
import spock.lang.Specification

class LanguageLibraryProjectInitDescriptorSpec extends Specification {

    FileResolver fileResolver = Mock()
    TemplateOperationFactory templateOperationFactory = Mock()
    LanguageLibraryProjectInitDescriptor descriptor
    TemplateOperationFactory.TemplateOperationBuilder templateOperationBuilder = Mock(TemplateOperationFactory.TemplateOperationBuilder)
    def setup(){

    }

    def "generates from template within sourceSet"(){
        setup:

        descriptor = new LanguageLibraryProjectInitDescriptor(language, templateOperationFactory, fileResolver)
        1 * templateOperationFactory.newTemplateOperation() >> templateOperationBuilder

        when:
        descriptor.fromClazzTemplate("someTemplate/SomeClazz.somelang.template", sourceSet)

        then:
        1 * templateOperationBuilder.withTemplate("someTemplate/SomeClazz.somelang.template") >> templateOperationBuilder
        1 * templateOperationBuilder.withTarget(target) >> templateOperationBuilder
        1 * templateOperationBuilder.create() >> Mock(TemplateOperation)
        where:
        language        |  sourceSet       |   target
        "somelang"      |    "main"        |   "src/main/somelang/SomeClazz.somelang"
        "someotherlang" |    "test"        |   "src/test/someotherlang/SomeClazz.somelang"
        "somelang"      |    "integTest"   |   "src/integTest/somelang/SomeClazz.somelang"
    }

    def "whenNoSourcesAvailable creates template operation checking for sources"(){
        setup:
        def mainSourceDirectory = Mock(FileTreeInternal)
        def testSourceDirectory = Mock(FileTreeInternal)
        def delegate = Mock(TemplateOperation)

        descriptor = new LanguageLibraryProjectInitDescriptor("somelang", templateOperationFactory, fileResolver)
        when:
        descriptor.whenNoSourcesAvailable(delegate).generate()
        then:
        1 * mainSourceDirectory.empty >> noMainSources
        _ * testSourceDirectory.empty >> noTestSources
        1 * fileResolver.resolveFilesAsTree("src/main/somelang") >> mainSourceDirectory
        _ * fileResolver.resolveFilesAsTree("src/test/somelang") >> testSourceDirectory

        delegateInvocation * delegate.generate()

        where:
        noMainSources | noTestSources |   delegateInvocation
        true          |    true       |  1
        true          |    false      |  1
        false         |    false      |  0
    }
}
