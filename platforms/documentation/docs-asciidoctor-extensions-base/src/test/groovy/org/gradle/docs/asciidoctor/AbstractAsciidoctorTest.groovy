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
package org.gradle.docs.asciidoctor

import org.asciidoctor.Asciidoctor
import spock.lang.Specification

/**
 * Creates an {@link Asciidoctor} instance for each test and shuts it down afterwards.
 */
abstract class AbstractAsciidoctorTest extends Specification {

    Asciidoctor asciidoctor

    def setup() {
        // Picks up GradleDocsConverterRegistry and GradleDocsAsciidoctorExtensionRegistry from META-INF/services
        asciidoctor = Asciidoctor.Factory.create()
    }

    def cleanup() {
        // Each instance holds a JRuby runtime, which is large enough to exhaust the test JVM heap if leaked
        asciidoctor.shutdown()
    }
}
