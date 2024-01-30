/*
 * Copyright 2021 the original author or authors.
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
import org.asciidoctor.extension.JavaExtensionRegistry
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification


class SampleIncludeProcessorTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    Asciidoctor asciidoctor

    def setup() {
        tmpDir.newFolder("src", "samples")
        asciidoctor = Asciidoctor.Factory.create()
        JavaExtensionRegistry extensionRegistry = asciidoctor.javaExtensionRegistry()
        extensionRegistry.includeProcessor(SampleIncludeProcessor.class)
    }

    def "requires dir and files attributes"() {
        given:
        String asciidocContent = """
= Doctitle

include::sample[]
"""

        when:
        asciidoctor.convert(asciidocContent, [:])

        then:
        thrown IllegalStateException
    }

    def "converts a sample include into declared source"() {
        given:
        tmpDir.newFile("src/samples/build.gradle") << """
task hello {
    doLast {
        println "hello world"
    }
}
"""

        String asciidocContent = """
= Doctitle
:samples-dir: ${tmpDir.root.canonicalPath}

include::sample[dir="src/samples",files="build.gradle[]"]
"""

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        content.contains('println "hello world"')
    }

    def "allows sample included with multiple files"() {
        given:
        tmpDir.newFile("src/samples/build.gradle") << """
task compile {
    doLast {
        println "compiling source"
    }
}
task testCompile(dependsOn: compile) {
    doLast {
        println "compiling test source"
    }
}
task test(dependsOn: [compile, testCompile]) {
    doLast {
        println "running unit tests"
    }
}
task build(dependsOn: [test])
"""
        tmpDir.newFile("src/samples/init.gradle") << """
useLogger(new CustomEventLogger())

class CustomEventLogger extends BuildAdapter implements TaskExecutionListener {
    // tag::before-execute[]
    public void beforeExecute(Task task) {
        println "[\$task.name]"
    }
    // end::before-execute[]

    public void afterExecute(Task task, TaskState state) {
        println()
    }

    public void buildFinished(BuildResult result) {
        println 'build completed'
        if (result.failure != null) {
            result.failure.printStackTrace()
        }
    }
}
"""

        String asciidocContent = """
= Doctitle
:samples-dir: ${tmpDir.root.canonicalPath}

include::sample[dir="src/samples",files="build.gradle[];init.gradle[tags=before-execute]"]
"""

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        content.contains('task compile')
        content.contains('public void beforeExecute')
        !content.contains('public void afterExecute')
    }

    def "allows sample included with tags"() {
        given:
        tmpDir.newFile("src/samples/build.gradle") << """
task hello {
    // tag::foo[]
    doLast {
    // end::foo[]
        println "hello world"
    // tag::bar[]
    }
    // end::bar[]
}
"""

        String asciidocContent = """
= Doctitle
:samples-dir: ${tmpDir.root.canonicalPath}

include::sample[dir="src/samples",files="build.gradle[tags=foo,bar]"]
"""

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        !content.contains('println "hello world"')
        content.contains("doLast {\n}")
    }

    /**
     * https://docs.asciidoctor.org/asciidoc/latest/directives/include-tagged-regions/#tag-filtering
     */
    def "allows sample included with #description"() {
        given:
        tmpDir.newFile("src/samples/build.gradle") << """
task hello {
    // tag::foo[]
    doLast {
    // end::foo[]
        println "hello world"
    // tag::bar[]
    }
    // end::bar[]
}
"""

        String asciidocContent = """
= Doctitle
:samples-dir: ${tmpDir.root.canonicalPath}

include::sample[dir="src/samples",files="build.gradle[${tag}]"]
"""

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        def expectedContent = '''task hello {
        |    doLast {
        |        println "hello world"
        |    }
        |}'''.stripMargin()

        content.contains(expectedContent)

        where:
        description           | tag
        "no tags"             | ""
        "double wildcard tag" | "tags=**"
    }

    def "allows sample included with tags in XML"() {
        given:
        tmpDir.newFile("src/samples/foo.xml") << """
<hello>
    <!-- tag::bar[] -->
    <child></child>
    <!-- end::bar[] -->
</hello>
"""

        String asciidocContent = """
= Doctitle
:samples-dir: ${tmpDir.root.canonicalPath}

include::sample[dir="src/samples",files="foo.xml[tag=bar]"]
"""

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        !content.contains('hello>')
        content.contains("&lt;child&gt;&lt;/child&gt;")
    }

    def "trims indentation in samples"() {
        given:
        tmpDir.newFile("src/samples/build.gradle") << """
            |    // Comment
            |    doLast {
            |        println "hello world"
            |    }
        """.trim().stripMargin()

        String asciidocContent = """
            |= Doctitle
            |:samples-dir: ${tmpDir.root.canonicalPath}
            |
            |include::sample[dir="src/samples",files="build.gradle[]"]
        """.trim().stripMargin()

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        def expectedContent = '''
            |// Comment
            |doLast {
            |    println "hello world"
            |}
        '''.trim().stripMargin()

        then:
        content.contains(expectedContent)
    }

    def "trims indentation in samples with tags"() {
        given:
        tmpDir.newFile("src/samples/build.gradle") << """
            |// No-indent comment outside of tag
            |// tag::foo[]
            |    // Comment
            |    doLast {
            |// end::foo[]
            |        println "hello world"
            |// tag::foo[]
            |    }
            |// end::foo[]
        """.trim().stripMargin()

        String asciidocContent = """
            |= Doctitle
            |:samples-dir: ${tmpDir.root.canonicalPath}
            |
            |include::sample[dir="src/samples",files="build.gradle[tags=foo]"]
        """.trim().stripMargin()

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        def expectedContent = """
            |// Comment
            |doLast {
            |}
        """.trim().stripMargin()

        then:
        content.contains(expectedContent)
    }
}
