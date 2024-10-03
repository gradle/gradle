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

package org.gradle.process.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class EffectiveJavaForkOptionsTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    private final fileCollectionFactory = TestFiles.fileCollectionFactory(tmpDir.testDirectory)
    private EffectiveJavaForkOptions options

    def setup() {
        options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))
    }

    def "defaults are compatible"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is compatible with identical options"() {
        def factory = {
            new EffectiveJavaForkOptions("/foo/bar", new File("foo"), ["FOO": "BAR"], new JvmOptions(fileCollectionFactory)).tap {
                jvmOptions.with {
                    systemProperties = ["foo": "bar", "bar": "foo"]
                    minHeapSize = "128m"
                    maxHeapSize = "256m"
                    debug = true
                }
            }
        }
        options = factory()
        def other = factory()

        expect:
        options.isCompatibleWith(other)
    }

    def "is compatible with different representations of heap options"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.with {
            minHeapSize = "1024m"
            maxHeapSize = "2g"
        }
        other.jvmOptions.with {
            minHeapSize = "1g"
            maxHeapSize = "2048m"
        }

        then:
        options.isCompatibleWith(other)
    }

    def "is compatible with lower heap requirements"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.with {
            minHeapSize = "128m"
            maxHeapSize = "1024m"
        }
        other.jvmOptions.with {
            minHeapSize = "64m"
            maxHeapSize = "512m"
        }

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with higher heap requirements"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.with {
            minHeapSize = "64m"
            maxHeapSize = "512m"
        }
        other.jvmOptions.with {
            minHeapSize = "128m"
            maxHeapSize = "1024m"
        }

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with the same set of jvm args"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.jvmArgs = ["-server", "-esa"]
        other.jvmOptions.jvmArgs = ["-esa", "-server"]

        then:
        options.isCompatibleWith(other)
    }

    def "is compatible with a subset of jvm args"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.jvmArgs = ["-server", "-esa"]
        other.jvmOptions.jvmArgs = ["-server"]

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with a superset of jvm args"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.jvmArgs = ["-server"]
        other.jvmOptions.jvmArgs = ["-server", "-esa"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is not compatible with a different set of jvm args"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.jvmArgs = ["-server", "-esa"]
        other.jvmOptions.jvmArgs = ["-client", "-esa"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same executable"() {
        options = new EffectiveJavaForkOptions("foo", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("foo", new File(""), [:], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different executables"() {
        options = new EffectiveJavaForkOptions("foo", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("bar", new File(""), [:], new JvmOptions(fileCollectionFactory))

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same workingDir"() {
        options = new EffectiveJavaForkOptions("", new File("foo"), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("",  new File("foo"), [:], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different workingDir"() {
        options = new EffectiveJavaForkOptions("", new File("foo"), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("",  new File("bar"), [:], new JvmOptions(fileCollectionFactory))

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same environment variables"() {
        options = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different environment variables"() {
        options = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), ["BAZ": "foo", "FOO": "bar"], new JvmOptions(fileCollectionFactory))

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with subset of environment variables"() {
        options = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar"], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with super set of environment variables"() {
        options = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar"], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with the same system properties"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.systemProperties = ["foo": "bar", "bar": "foo"]
        other.jvmOptions.systemProperties = ["bar": "foo", "foo": "bar"]

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different system properties"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.systemProperties = ["foo": "bar", "bar": "foo"]
        other.jvmOptions.systemProperties = ["baz": "foo", "foo": "bar"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with subset of system properties"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.systemProperties = ["foo": "bar", "bar": "foo"]
        other.jvmOptions.systemProperties = ["foo": "bar"]

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with super set of system properties"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.systemProperties = ["foo": "bar"]
        other.jvmOptions.systemProperties = ["foo": "bar", "bar": "foo"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same debug setting"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.debug = true
        other.jvmOptions.debug = true

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different debug setting"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.debug = true
        other.jvmOptions.debug = false

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same enableAssertions setting"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.enableAssertions = true
        other.jvmOptions.enableAssertions = true

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different enableAssertions setting"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.enableAssertions = true
        other.jvmOptions.enableAssertions = false

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same bootstrapClasspath"() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        def options = new EffectiveJavaForkOptions("",  new File(systemSpecificAbsolutePath("foo")), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("",  new File(systemSpecificAbsolutePath("foo")), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.with {
            bootstrapClasspath(files)
        }
        other.jvmOptions.with {
            bootstrapClasspath(files)
        }

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different bootstrapClasspath"() {
        def files1 = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        def files2 = ['file2.jar', 'file3.jar'].collect { new File(it).canonicalFile }
        def options = new EffectiveJavaForkOptions("",  new File(systemSpecificAbsolutePath("foo")), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("",  new File(systemSpecificAbsolutePath("foo")), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.with {
            bootstrapClasspath(files1)
        }
        other.jvmOptions.with {
            bootstrapClasspath(files2)
        }

        then:
        !options.isCompatibleWith(other)
    }

    def "string values are trimmed"() {
        options = new EffectiveJavaForkOptions(" foo", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("foo ", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.with {
            minHeapSize = "128m "
            maxHeapSize = "1g"
            jvmArgs = [" -server", "-esa"]
        }
        other.jvmOptions.with {
            minHeapSize = "128m"
            maxHeapSize = " 1g"
            jvmArgs = [" -server", "-esa "]
        }

        then:
        options.isCompatibleWith(other)
    }

    def "capitalization of memory options is irrelevant"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.with {
            minHeapSize = "128M"
            maxHeapSize = "1g"
        }
        other.jvmOptions.with {
            minHeapSize = "128m"
            maxHeapSize = "1G"
        }

        then:
        options.isCompatibleWith(other)
    }

    def "capitalization of JVM args is relevant"() {
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        options.jvmOptions.with {
            jvmArgs = ["-Server", "-esa"]
        }
        other.jvmOptions.with {
            jvmArgs = ["-server", "-esa"]
        }

        then:
        !options.isCompatibleWith(other)
    }

    def "unspecified heap options are only compatible with unspecified heap options"() {
        def other1 = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other2 = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        when:
        other2.jvmOptions.with {
            minHeapSize = "128m"
            maxHeapSize = "256m"
        }

        then:
        options.isCompatibleWith(other1)
        !options.isCompatibleWith(other2)
    }

    def "unspecified executable is only compatible with unspecified executable options"() {
        options = new EffectiveJavaForkOptions(null, new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other1 = new EffectiveJavaForkOptions(null, new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other2 = new EffectiveJavaForkOptions("/foo/bar", new File(""), [:], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other1)
        !options.isCompatibleWith(other2)
    }
}
