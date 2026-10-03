/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.internal.serialize

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.tools.ToolProvider
import java.util.concurrent.Callable

class ExceptionSerializationUtilTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "falls back to the standard cause when an exception method signature cannot be resolved"() {
        given:
        TestFile sourceDir = temporaryFolder.createDir('src/repro')
        TestFile classesDir = temporaryFolder.createDir('classes')
        TestFile missingTypeSource = sourceDir.file('MissingSignatureType.java')
        missingTypeSource.text = '''
            package repro;

            public final class MissingSignatureType {
            }
        '''.stripIndent()
        TestFile brokenExceptionSource = sourceDir.file('BrokenException.java')
        brokenExceptionSource.text = '''
            package repro;

            public final class BrokenException extends RuntimeException {
                public BrokenException(String message, Throwable cause) {
                    super(message, cause);
                }

                public MissingSignatureType methodWithMissingReturnType() {
                    return null;
                }
            }
        '''.stripIndent()
        TestFile exceptionFactorySource = sourceDir.file('BrokenExceptionFactory.java')
        exceptionFactorySource.text = '''
            package repro;

            import java.util.concurrent.Callable;

            public final class BrokenExceptionFactory implements Callable<Throwable> {
                @Override
                public Throwable call() {
                    return new BrokenException("broken", new IllegalStateException("actual cause"));
                }
            }
        '''.stripIndent()
        assert ToolProvider.systemJavaCompiler.run(
            null,
            null,
            null,
            '-d',
            classesDir.absolutePath,
            missingTypeSource.absolutePath,
            brokenExceptionSource.absolutePath,
            exceptionFactorySource.absolutePath
        ) == 0
        assert classesDir.file('repro/MissingSignatureType.class').delete()
        URLClassLoader classLoader = new URLClassLoader([classesDir.toURI().toURL()] as URL[], ClassLoader.platformClassLoader)
        Callable<Throwable> exceptionFactory = Callable.class.cast(
            Class.forName('repro.BrokenExceptionFactory', true, classLoader).getDeclaredConstructor().newInstance()
        )
        Throwable exception = exceptionFactory.call()

        when:
        List<? extends Throwable> causes = ExceptionSerializationUtil.extractCauses(exception)

        then:
        causes*.message == ['actual cause']

        cleanup:
        classLoader?.close()
    }

    def "falls back to the standard cause when the causes collection fails in #operation during iteration #failingIteration"() {
        given:
        def standardCause = new IllegalStateException('actual cause')
        def discoveredCause = new IllegalArgumentException('discovered cause')
        int iterationCount = 0
        Collection<Throwable> causes = Stub(Collection) {
            isEmpty() >> {
                if (operation == 'isEmpty') {
                    throw new NoClassDefFoundError('MissingCollectionDependency')
                }
                false
            }
            size() >> {
                if (operation == 'size') {
                    throw new NoClassDefFoundError('MissingCollectionDependency')
                }
                1
            }
            iterator() >> {
                int currentIteration = ++iterationCount
                if (operation == 'iterator' && currentIteration == failingIteration) {
                    throw new NoClassDefFoundError('MissingCollectionDependency')
                }
                def delegateIterator = [discoveredCause].iterator()
                Stub(Iterator) {
                    hasNext() >> {
                        if (operation == 'hasNext' && currentIteration == failingIteration) {
                            throw new NoClassDefFoundError('MissingCollectionDependency')
                        }
                        delegateIterator.hasNext()
                    }
                    next() >> {
                        if (operation == 'next' && currentIteration == failingIteration) {
                            throw new NoClassDefFoundError('MissingCollectionDependency')
                        }
                        delegateIterator.next()
                    }
                }
            }
        }
        def exception = new ExternalMultiCauseException(causes, standardCause)

        expect:
        ExceptionSerializationUtil.extractCauses(exception) == [standardCause]

        where:
        operation  | failingIteration
        'isEmpty'  | 0
        'size'     | 0
        'iterator' | 1
        'iterator' | 2
        'hasNext'  | 1
        'hasNext'  | 2
        'next'     | 1
        'next'     | 2
    }

    static class ExternalMultiCauseException extends RuntimeException {
        private final Collection<? extends Throwable> causes

        ExternalMultiCauseException(Collection<? extends Throwable> causes, Throwable cause) {
            super('external failure', cause)
            this.causes = causes
        }

        Collection<? extends Throwable> getCauses() {
            return causes
        }
    }
}
