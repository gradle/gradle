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

    def "defaults are compatible"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is compatible with identical options"() {
        def factory = {
            new EffectiveJavaForkOptions("/foo/bar", new File("foo"), ["FOO": "BAR"], new JvmOptions(fileCollectionFactory).tap {
                systemProperties = ["foo": "bar", "bar": "foo"]
                minHeapSize = "128m"
                maxHeapSize = "256m"
                debug = true
            })
        }
        def options = factory()
        def other = factory()

        expect:
        options.isCompatibleWith(other)
    }

    def "is compatible with different representations of heap options"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "1024m"
            maxHeapSize = "2g"
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "1g"
            maxHeapSize = "2048m"
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is compatible with lower heap requirements"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "128m"
            maxHeapSize = "1024m"
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "64m"
            maxHeapSize = "512m"
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with higher heap requirements"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "64m"
            maxHeapSize = "512m"
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "128m"
            maxHeapSize = "1024m"
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with the same set of jvm args"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-server", "-esa"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-server", "-esa"]
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is compatible with a subset of jvm args"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-server", "-esa"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-server"]
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with a superset of jvm args"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-server"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-server", "-esa"]
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "is not compatible with a different set of jvm args"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-server", "-esa"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-client", "-esa"]
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same executable"() {
        def options = new EffectiveJavaForkOptions("foo", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("foo", new File(""), [:], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different executables"() {
        def options = new EffectiveJavaForkOptions("foo", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("bar", new File(""), [:], new JvmOptions(fileCollectionFactory))

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same workingDir"() {
        def options = new EffectiveJavaForkOptions("", new File("foo"), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("",  new File("foo"), [:], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different workingDir"() {
        def options = new EffectiveJavaForkOptions("", new File("foo"), [:], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("",  new File("bar"), [:], new JvmOptions(fileCollectionFactory))

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same environment variables"() {
        def options = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different environment variables"() {
        def options = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), ["BAZ": "foo", "FOO": "bar"], new JvmOptions(fileCollectionFactory))

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with subset of environment variables"() {
        def options = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar"], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with super set of environment variables"() {
        def options = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar"], new JvmOptions(fileCollectionFactory))
        def other = new EffectiveJavaForkOptions("", new File(""), ["FOO": "bar", "BAR": "foo"], new JvmOptions(fileCollectionFactory))

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with the same system properties"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            systemProperties = ["foo": "bar", "bar": "foo"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            systemProperties = ["bar": "foo", "foo": "bar"]
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different system properties"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            systemProperties = ["foo": "bar", "bar": "foo"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            systemProperties = ["baz": "foo", "foo": "bar"]
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with subset of system properties"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            systemProperties = ["foo": "bar", "bar": "foo"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            systemProperties = ["foo": "bar"]
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with super set of system properties"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            systemProperties = ["foo": "bar"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            systemProperties = ["foo": "bar", "bar": "foo"]
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same debug setting"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            debug = true
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            debug = true
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different debug setting"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            debug = true
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            debug = false
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same enableAssertions setting"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            enableAssertions = true
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            enableAssertions = true
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different enableAssertions setting"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            enableAssertions = true
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            enableAssertions = false
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same bootstrapClasspath"() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        def options = new EffectiveJavaForkOptions("",  new File(systemSpecificAbsolutePath("foo")), [:], new JvmOptions(fileCollectionFactory).tap {
            bootstrapClasspath(files)
        })
        def other = new EffectiveJavaForkOptions("",  new File(systemSpecificAbsolutePath("foo")), [:], new JvmOptions(fileCollectionFactory).tap {
            bootstrapClasspath(files)
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different bootstrapClasspath"() {
        def files1 = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        def files2 = ['file2.jar', 'file3.jar'].collect { new File(it).canonicalFile }
        def options = new EffectiveJavaForkOptions("",  new File(systemSpecificAbsolutePath("foo")), [:], new JvmOptions(fileCollectionFactory).tap {
            bootstrapClasspath(files1)
        })
        def other = new EffectiveJavaForkOptions("",  new File(systemSpecificAbsolutePath("foo")), [:], new JvmOptions(fileCollectionFactory).tap {
            bootstrapClasspath(files2)
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "string values are trimmed"() {
        def options = new EffectiveJavaForkOptions(" foo", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "128m "
            maxHeapSize = "1g"
            jvmArgs = [" -server", "-esa"]
        })
        def other = new EffectiveJavaForkOptions("foo ", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "128m"
            maxHeapSize = " 1g"
            jvmArgs = [" -server", "-esa "]
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "capitalization of memory options is irrelevant"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "128M"
            maxHeapSize = "1g"
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "128m"
            maxHeapSize = "1G"
        })

        expect:
        options.isCompatibleWith(other)
    }

    def "capitalization of JVM args is relevant"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-Server", "-esa"]
        })
        def other = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            jvmArgs = ["-server", "-esa"]
        })

        expect:
        !options.isCompatibleWith(other)
    }

    def "unspecified heap options are only compatible with unspecified heap options"() {
        def options = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other1 = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other2 = new EffectiveJavaForkOptions("", new File(""), [:], new JvmOptions(fileCollectionFactory).tap {
            minHeapSize = "128m"
            maxHeapSize = "256m"
        })

        expect:
        options.isCompatibleWith(other1)
        !options.isCompatibleWith(other2)
    }

    def "unspecified executable is only compatible with unspecified executable options"() {
        def options = new EffectiveJavaForkOptions(null, new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other1 = new EffectiveJavaForkOptions(null, new File(""), [:], new JvmOptions(fileCollectionFactory))
        def other2 = new EffectiveJavaForkOptions("/foo/bar", new File(""), [:], new JvmOptions(fileCollectionFactory))

        expect:
        options.isCompatibleWith(other1)
        !options.isCompatibleWith(other2)
    }
}
