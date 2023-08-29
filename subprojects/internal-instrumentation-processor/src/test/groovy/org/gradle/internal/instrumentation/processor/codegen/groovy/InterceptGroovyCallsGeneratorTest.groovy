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

package org.gradle.internal.instrumentation.processor.codegen.groovy

import com.google.testing.compile.Compilation
import org.gradle.internal.instrumentation.InstrumentationCodeGenTest

import static com.google.testing.compile.CompilationSubject.assertThat

class InterceptGroovyCallsGeneratorTest extends InstrumentationCodeGenTest {

    def "should generate interceptor declaration with public modifier"() {
        given:
        def givenSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import java.io.File;

            @SpecificGroovyCallInterceptors(generatedClassName = "my.InterceptorDeclaration_GroovyBytecodeImpl")
            public class FileInterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                public static File[] intercept_listFiles(@Receiver File thisFile) {
                    return new File[0];
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def expectedJvmInterceptors = source """
            package my;
            public class InterceptorDeclaration_GroovyBytecodeImpl {
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedJvmInterceptors))
            .containsElementsIn(expectedJvmInterceptors)
    }
}
