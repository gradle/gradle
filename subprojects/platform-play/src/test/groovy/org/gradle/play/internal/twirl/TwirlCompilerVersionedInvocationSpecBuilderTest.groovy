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

package org.gradle.play.internal.twirl

import spock.lang.Specification

import java.nio.charset.Charset

class TwirlCompilerVersionedInvocationSpecBuilderTest extends Specification {

    TwirlCompileSpec spec = Mock(TwirlCompileSpec)
    ScalaCodecMapper codeMapper = Mock(ScalaCodecMapper)
    File sourceDir = new File("sourceDirectory")
    File destinationDir = new File("destinationDir")
    Charset charSet = Charset.forName("utf-8")
    File templateSourceFile = new File(sourceDir, "source.html.scala");

    TwirlCompilerVersionedInvocationSpecBuilder builder = new TwirlCompilerVersionedInvocationSpecBuilder(codeMapper)

    def setup(){
        1 * spec.getSourceDirectory() >> sourceDir
        1 * spec.getDestinationDir() >> destinationDir
        1 * spec.getFormatterType() >> "some.formatter"
        1 * spec.getAdditionalImports() >> "additional_import"
    }

    def "can build 2.2.3 invocation spec"(){
        setup:
        1 * spec.getCompilerVersion() >> "2.2.3"
        File templateSourceFile = new File(sourceDir, "source.html.scala");
        when:
        def invocationSpec = builder.build(spec);
        then:
        invocationSpec.version == TwirlCompilerVersion.V_22X
        invocationSpec.getParameter(templateSourceFile).size() == invocationSpec.getParameter(templateSourceFile).size()
        invocationSpec.getParameterTypes() == [File.class, File.class, File.class, String.class, String.class]
        invocationSpec.getParameter(templateSourceFile) == [templateSourceFile, sourceDir, destinationDir, "some.formatter", "additional_import"]
    }

    def "can build 1.0.2 invocation spec"(){
        setup:
        1 * spec.getCompilerVersion() >> "1.0.2"
        1 * spec.getCodec() >> "utf-8"
        // placeholder instead of using scala io
        1 * codeMapper.map("utf-8") >> charSet
        1 * spec.isInclusiveDots() >> true
        1 * spec.isUseOldParser() >> true

        when:
        def invocationSpec = builder.build(spec);
        then:
        invocationSpec.version == TwirlCompilerVersion.V_102
        invocationSpec.getParameter(templateSourceFile).size() == invocationSpec.getParameter(templateSourceFile).size()
        invocationSpec.getParameterTypes() == [File.class, File.class, File.class, String.class, String.class, charSet.getClass(), boolean.class, boolean.class]
        invocationSpec.getParameter(templateSourceFile) == [templateSourceFile, sourceDir, destinationDir, "some.formatter", "additional_import", charSet, true, true]

    }
}
