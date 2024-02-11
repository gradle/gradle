/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.integtests.fixtures.Sample
import org.junit.Rule

class SamplesJavaIncrementalAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final Sample processing = new Sample(temporaryFolder, 'java/incrementalAnnotationProcessing')

    def "isolating annotation processors are incremental"() {
        given:
        CompilationOutputsFixture outputs = new CompilationOutputsFixture(processing.dir.file("groovy/user/build/classes"))
        outputs.snapshot { compile() }

        when:
        processing.dir.file("groovy/user/src/main/java/Entity1.java").text = """
        @Entity
        public class Entity1 {
            public void hasChanged() {}
        }
        """
        compile()

        then:
        outputs.recompiledClasses("Entity1", "Entity1Repository", "ServiceRegistry", "Main")
    }

    def "aggregating annotation processors are incremental"() {
        given:
        CompilationOutputsFixture outputs = new CompilationOutputsFixture(processing.dir.file("groovy/user/build/classes"))
        outputs.snapshot { compile() }

        when:
        processing.dir.file("groovy/user/src/main/java/Service1.java").text = """
        @Service
        public class Service1 {
            public void hasChanged() {}
        }
        """
        compile()

        then:
        outputs.recompiledClasses("Service1", "ServiceRegistry", "Main")
    }

    def compile() {
        inDirectory(processing.dir.file('groovy'))
        succeeds("compileJava")
    }
}
