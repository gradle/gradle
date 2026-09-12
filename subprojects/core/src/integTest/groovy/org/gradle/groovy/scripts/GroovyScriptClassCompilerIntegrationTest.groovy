/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.groovy.scripts

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import java.util.function.Supplier

class GroovyScriptClassCompilerIntegrationTest extends AbstractIntegrationSpec {
    def "remapped scripts correctly handle method references in statically compiled code"() {
        // TODO(mlopatkin) can the class reference be generated in some other class?
        buildFile """
        import ${Supplier.name}

        String method() {
            return "value"
        }

        @${CompileStatic.name}
        String callThroughReference() {
            Supplier<String> reference = this::method
            return reference.get()
        }

        tasks.register("echo") {
            String value = callThroughReference()
            doLast { println("value = \$value") }
        }
        """

        when:
        succeeds("echo")

        then:
        outputContains("value = value")
    }

    def "classes declared in a script get the implicit default constructor and the metaclass accessors"() {
        // Groovy's Verifier is what adds these. It runs as part of the CLASS_GENERATION phase
        // operations that CustomCompilationUnit has to re-register, so make sure none of them get
        // lost - without the Verifier, `new Thing()` fails at runtime with
        // "Could not find matching constructor for: Thing()".
        buildFile """
        class Thing {
        }

        class ThingWithAField {
            def value = "field"
        }

        tasks.register("echo") {
            doLast {
                println("declaredConstructors = \$Thing.declaredConstructors")
                println("thingWithAField = \${new ThingWithAField().value}")
                println("metaClass = \${new Thing().metaClass != null}")
            }
        }
        """

        when:
        succeeds("echo")

        then:
        outputContains("declaredConstructors = [public Thing()]")
        outputContains("thingWithAField = field")
        outputContains("metaClass = true")
    }
}
