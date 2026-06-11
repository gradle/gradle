/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.classpath.transforms

import org.gradle.internal.classpath.types.InstrumentationTypeRegistry
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter
import spock.lang.Specification
import spock.lang.TempDir

import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate

/**
 * Verifies the class-load-time transform classifies classes as project or external by code-source origin.
 */
class InstrumentingClassLoadTimeTransformTest extends Specification {

    @TempDir
    File dir

    def "classifies a class by the origin of its code source"() {
        given:
        def projectJar = new File(dir, "project.jar")
        def externalJar = new File(dir, "external.jar")
        def transform = new InstrumentingClassLoadTimeTransform(
            BytecodeInterceptorFilter.INSTRUMENTATION_AND_BYTECODE_UPGRADE,
            InstrumentationTypeRegistry.EMPTY,
            BytecodeInterceptorFilter.INSTRUMENTATION_ONLY,
            InstrumentationTypeRegistry.EMPTY,
            [projectJar] as Set
        )

        expect:
        transform.isProjectOrigin(protectionDomainFor(projectJar))
        !transform.isProjectOrigin(protectionDomainFor(externalJar))
        !transform.isProjectOrigin(null)
        !transform.isProjectOrigin(new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null))
    }

    private static ProtectionDomain protectionDomainFor(File file) {
        new ProtectionDomain(new CodeSource(file.toURI().toURL(), (Certificate[]) null), null)
    }
}
