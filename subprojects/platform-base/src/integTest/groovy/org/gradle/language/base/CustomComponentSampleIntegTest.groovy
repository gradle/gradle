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

package org.gradle.language.base
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.junit.Rule

class CustomComponentSampleIntegTest extends AbstractIntegrationSpec {
    @Rule Sample customComponent = new Sample(temporaryFolder, "customComponent")

    def "can create custom component with binaries"() {
        given:
        sample customComponent
        customComponent.dir.file("build.gradle") << """

task checkModel << {
    assert project.componentSpecs.size() == 2
    def titleAImage = project.componentSpecs.TitleA
    assert titleAImage instanceof ImageComponent
    assert titleAImage.projectPath == project.path
    assert titleAImage.displayName == "DefaultImageComponent 'TitleA'"
    assert titleAImage.binaries.collect{it.name}.sort() == ['titleA14pxBinary', 'titleA28pxBinary', 'titleA40pxBinary']
}

"""
        expect:
        succeeds "checkModel"
    }

    def "can create all binaries"() {
        given:
        sample customComponent
        when:
        succeeds "assemble"
        then:
        executedAndNotSkipped ":renderTitleA14pxSvg", ":titleA14pxBinary", ":renderTitleA28pxSvg", ":titleA28pxBinary", ":renderTitleA40pxSvg",
                              ":titleA40pxBinary", ":renderTitleB14pxSvg", ":titleB14pxBinary", ":renderTitleB28pxSvg", ":titleB28pxBinary",
                              ":renderTitleB40pxSvg", ":titleB40pxBinary", ":assemble"

        and:
        customComponent.dir.file("build/renderedSvg").assertHasDescendants("TitleA_14px.svg", "TitleA_28px.svg", "TitleA_40px.svg", "TitleB_14px.svg",
                                                                         "TitleB_28px.svg", "TitleB_40px.svg")
    }
}
