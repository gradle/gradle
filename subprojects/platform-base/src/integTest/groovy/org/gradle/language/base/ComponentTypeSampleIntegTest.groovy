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
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.junit.Rule

@UnsupportedWithInstantExecution(because = "software model")
class ComponentTypeSampleIntegTest extends AbstractIntegrationSpec {
    @Rule Sample componentTypeSample = new Sample(temporaryFolder, "customModel/componentType")

    def "can create custom component with binaries"() {
        given:
        sample componentTypeSample
        componentTypeSample.dir.file("build.gradle") << '''

model {
    tasks {
        create("checkModel") {
            def components = $.components
            doLast {
                assert components.size() == 2
                def titleAImage = components.imageA
                assert titleAImage instanceof ImageComponent
                assert titleAImage.projectPath == project.path
                assert titleAImage.displayName == "ImageComponent 'imageA'"
                assert titleAImage.title == 'TitleA'
                assert titleAImage.binaries.values()*.name.sort() == ['14px', '28px', '40px']
            }
        }
    }
}
'''
        expect:
        succeeds "checkModel"
    }

    def "can create all binaries"() {
        given:
        sample componentTypeSample
        when:
        succeeds "assemble"
        then:
        executedAndNotSkipped ":renderImageA14pxSvg", ":imageA14px", ":renderImageA28pxSvg", ":imageA28px", ":renderImageA40pxSvg",
                              ":imageA40px", ":renderImageB14pxSvg", ":imageB14px", ":renderImageB28pxSvg", ":imageB28px",
                              ":renderImageB40pxSvg", ":imageB40px", ":assemble"

        and:
        componentTypeSample.dir.file("build/renderedSvg").assertHasDescendants("TitleA_14px.svg", "TitleA_28px.svg", "TitleA_40px.svg", "TitleB_14px.svg",
                                                                         "TitleB_28px.svg", "TitleB_40px.svg")
    }
}
