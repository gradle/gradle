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

package org.gradle.internal.instrumentation.extensions.types

import com.google.testing.compile.Compilation
import org.gradle.internal.instrumentation.InstrumentationCodeGenTest

import java.nio.charset.StandardCharsets

import static com.google.testing.compile.CompilationSubject.assertThat
import static javax.tools.StandardLocation.CLASS_OUTPUT

class InstrumentedTypesResourceGeneratorTest extends InstrumentationCodeGenTest {

    def "should generate a resource with types with intercept inherited methods"() {
        given:
        def givenSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import org.gradle.internal.classpath.declarations.*;
            import org.gradle.api.*;
            import java.io.File;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl")
            public class InterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                public static File[] intercept_listFiles(@Receiver File thisFile) {
                    return new File[0];
                }

                @InterceptCalls
                @InstanceMethod
                @InterceptInherited
                public static boolean intercept_getEnabled(@Receiver Task thisTask) {
                    return false;
                }

                @InterceptCalls
                @InstanceMethod
                @InterceptInherited
                public static String intercept_getDescription(@Receiver Rule thisRule) {
                    return "";
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
                .generatedFile(CLASS_OUTPUT, "META-INF/gradle/instrumentation/instrumented-classes.txt")
                .contentsAsString(StandardCharsets.UTF_8)
                .isEqualTo("org/gradle/api/Rule\norg/gradle/api/Task")
    }
}
