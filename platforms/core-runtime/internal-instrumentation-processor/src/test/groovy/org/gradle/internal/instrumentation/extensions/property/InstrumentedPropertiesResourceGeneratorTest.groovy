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

package org.gradle.internal.instrumentation.extensions.property

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.testing.compile.Compilation
import org.gradle.internal.instrumentation.InstrumentationCodeGenTest

import java.nio.charset.StandardCharsets

import static com.google.testing.compile.CompilationSubject.assertThat
import static javax.tools.StandardLocation.CLASS_OUTPUT
import static org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility.ACCESSORS_REMOVED
import static org.gradle.internal.instrumentation.extensions.property.InstrumentedPropertiesResourceGenerator.UpgradedProperty
import static org.gradle.internal.instrumentation.extensions.property.InstrumentedPropertiesResourceGenerator.ReplacedAccessor

class InstrumentedPropertiesResourceGeneratorTest extends InstrumentationCodeGenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()

    def "should generate a resource with upgraded properties sorted alphabetically"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

            public abstract class Task {
                @ReplacesEagerProperty(fluentSetter = true)
                public abstract Property<String> getTargetCompatibility();
                @ReplacesEagerProperty
                public abstract Property<String> getSourceCompatibility();
                @ReplacesEagerProperty(originalType = int.class)
                public abstract Property<Integer> getMaxErrors();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def maxErrorAccessors = [
                // Order is important
                new ReplacedAccessor("getMaxErrors", "()I", ACCESSORS_REMOVED),
                new ReplacedAccessor("setMaxErrors", "(I)V", ACCESSORS_REMOVED)
        ]
        def sourceCompatibilityAccessors = [
                // Order is important
                new ReplacedAccessor("getSourceCompatibility", "()Ljava/lang/String;", ACCESSORS_REMOVED),
                new ReplacedAccessor("setSourceCompatibility", "(Ljava/lang/String;)V", ACCESSORS_REMOVED)
        ]
        def targetCompatibilityAccessors = [
                // Order is important
                new ReplacedAccessor("getTargetCompatibility", "()Ljava/lang/String;", ACCESSORS_REMOVED),
                new ReplacedAccessor("setTargetCompatibility", "(Ljava/lang/String;)Lorg/gradle/test/Task;", ACCESSORS_REMOVED)
        ]
        def properties = [
                // Order is important
                new UpgradedProperty("org.gradle.test.Task", "maxErrors", "getMaxErrors", "()Lorg/gradle/api/provider/Property;", maxErrorAccessors),
                new UpgradedProperty("org.gradle.test.Task", "sourceCompatibility", "getSourceCompatibility", "()Lorg/gradle/api/provider/Property;", sourceCompatibilityAccessors),
                new UpgradedProperty("org.gradle.test.Task", "targetCompatibility", "getTargetCompatibility", "()Lorg/gradle/api/provider/Property;", targetCompatibilityAccessors)
        ]
        assertThat(compilation)
            .generatedFile(CLASS_OUTPUT, "META-INF/gradle/instrumentation/upgraded-properties.json")
            .contentsAsString(StandardCharsets.UTF_8)
            .isEqualTo(MAPPER.writeValueAsString(properties))
    }

    def "should generate json properties ordered alphabetically"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

            public abstract class Task {
                @ReplacesEagerProperty
                public abstract Property<String> getSourceCompatibility();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        assertThat(compilation)
            .generatedFile(CLASS_OUTPUT, "META-INF/gradle/instrumentation/upgraded-properties.json")
            .contentsAsString(StandardCharsets.UTF_8)
            .isEqualTo("[{\"containingType\":\"org.gradle.test.Task\",\"methodDescriptor\":\"()Lorg/gradle/api/provider/Property;\",\"methodName\":\"getSourceCompatibility\",\"propertyName\":\"sourceCompatibility\",\"replacedAccessors\":[{\"binaryCompatibility\":\"ACCESSORS_REMOVED\",\"descriptor\":\"()Ljava/lang/String;\",\"name\":\"getSourceCompatibility\"},{\"binaryCompatibility\":\"ACCESSORS_REMOVED\",\"descriptor\":\"(Ljava/lang/String;)V\",\"name\":\"setSourceCompatibility\"}]}]")
    }
}
