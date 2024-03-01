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

package org.gradle.internal.instrumentation.processor.codegen.jvmbytecode

import com.google.testing.compile.Compilation
import org.gradle.internal.instrumentation.InstrumentationCodeGenTest

import java.nio.charset.StandardCharsets

import static com.google.testing.compile.CompilationSubject.assertThat
import static javax.tools.StandardLocation.CLASS_OUTPUT

class InterceptJvmCallsResourceGeneratorTest extends InstrumentationCodeGenTest {

    def "should generate a META-INF/services resource with all JvmBytecodeCallInterceptor factory classes"() {
        given:
        def givenFirstSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import org.gradle.internal.classpath.declarations.*;
            import org.gradle.api.*;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl1")
            public class RuleInterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                public static String intercept_getDescription(@Receiver Rule thisRule) {
                    return "";
                }
            }
        """
        def givenSecondSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import org.gradle.internal.classpath.declarations.*;
            import org.gradle.api.*;
            import java.io.*;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl2")
            public class FileInterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                public static File[] intercept_listFiles(@Receiver File thisFile) {
                    return new File[0];
                }
            }
        """

        when:
        Compilation compilation = compile(givenFirstSource, givenSecondSource)

        then:
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedFile(CLASS_OUTPUT, "META-INF/services/org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor\$Factory")
            .contentsAsString(StandardCharsets.UTF_8)
            .isEqualTo([
                "my.InterceptorDeclaration_JvmBytecodeImpl1\$Factory",
                "my.InterceptorDeclaration_JvmBytecodeImpl2\$Factory"
            ].join("\n"))
    }
}
