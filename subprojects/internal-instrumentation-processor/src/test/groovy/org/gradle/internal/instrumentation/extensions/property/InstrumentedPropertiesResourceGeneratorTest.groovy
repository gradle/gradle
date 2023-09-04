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
import static org.gradle.internal.instrumentation.extensions.property.InstrumentedPropertiesResourceGenerator.PropertyEntry
import static org.gradle.internal.instrumentation.extensions.property.InstrumentedPropertiesResourceGenerator.UpgradedMethod

class InstrumentedPropertiesResourceGeneratorTest extends InstrumentationCodeGenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()

    def "should generate a resource with upgraded properties sorted alphabetically"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;

            public abstract class Task {
                @UpgradedProperty(fluentSetter = true)
                public abstract Property<String> getTargetCompatibility();
                @UpgradedProperty
                public abstract Property<String> getSourceCompatibility();
                @UpgradedProperty(originalType = int.class)
                public abstract Property<Integer> getMaxErrors();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def maxErrorMethods = [
            // Order is important
            new UpgradedMethod("getMaxErrors", "()I"),
            new UpgradedMethod("setMaxErrors", "(I)V")
        ]
        def sourceCompatibilityMethods = [
            // Order is important
            new UpgradedMethod("getSourceCompatibility", "()Ljava/lang/String;"),
            new UpgradedMethod("setSourceCompatibility", "(Ljava/lang/String;)V")
        ]
        def targetCompatibilityMethods = [
            // Order is important
            new UpgradedMethod("getTargetCompatibility", "()Ljava/lang/String;"),
            new UpgradedMethod("setTargetCompatibility", "(Ljava/lang/String;)Lorg/gradle/test/Task;")
        ]
        def properties = [
            // Order is important
            new PropertyEntry("org.gradle.test.Task", "maxErrors", maxErrorMethods),
            new PropertyEntry("org.gradle.test.Task", "sourceCompatibility", sourceCompatibilityMethods),
            new PropertyEntry("org.gradle.test.Task", "targetCompatibility", targetCompatibilityMethods)
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
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;

            public abstract class Task {
                @UpgradedProperty
                public abstract Property<String> getSourceCompatibility();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        assertThat(compilation)
            .generatedFile(CLASS_OUTPUT, "META-INF/gradle/instrumentation/upgraded-properties.json")
            .contentsAsString(StandardCharsets.UTF_8)
            .isEqualTo("[{\"containingType\":\"org.gradle.test.Task\",\"propertyName\":\"sourceCompatibility\",\"upgradedMethods\":[{\"descriptor\":\"()Ljava/lang/String;\",\"name\":\"getSourceCompatibility\"},{\"descriptor\":\"(Ljava/lang/String;)V\",\"name\":\"setSourceCompatibility\"}]}]")
    }
}
