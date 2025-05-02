/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.classloader

import spock.lang.Specification

class TransformErrorHandlerTest extends Specification {
    private static final LOADER_NAME = "test-loader"

    def "does nothing if there is no exception in the scope"() {
        given:
        TransformErrorHandler handler = handler()

        when:
        handler.enterClassLoadingScope("some/Class")
        handler.exitClassLoadingScope()

        then:
        noExceptionThrown()
    }

    def "rethrows the #expectedExceptionClass.simpleName if the scope exits with #exception.class.simpleName"() {
        given:
        TransformErrorHandler handler = handler()

        when:
        handler.enterClassLoadingScope("some/Class")
        handler.exitClassLoadingScopeWithException(exception)

        then:
        Throwable th = thrown(expectedExceptionClass)
        th.getCause() == expectedCause

        where:
        exception                                     || expectedExceptionClass | expectedCause
        new Error("error")                            || Error                  | null
        new RuntimeException("runtime")               || RuntimeException       | null
        new ClassNotFoundException("class not found") || ClassNotFoundException | null
        new IOException("I/O")                        || RuntimeException       | exception
    }

    def "rethrows the pending throwable if the scope exits normally"() {
        given:
        TransformErrorHandler handler = handler()
        def className = "some/Class"

        when:
        handler.enterClassLoadingScope(className)
        handler.classLoadingError(className, new IOException("I/O"))
        handler.exitClassLoadingScope()

        then:
        def e = thrown(ClassNotFoundException)
        e.cause instanceof IOException
        e.message.contains("class $className in $LOADER_NAME")
    }

    def "suppresses the pending exception if scope exits with exception"() {
        given:
        TransformErrorHandler handler = handler()
        def className = "some/Class"

        when:
        handler.enterClassLoadingScope(className)
        handler.classLoadingError(className, new IOException("I/O"))
        handler.exitClassLoadingScopeWithException(new RuntimeException("runtime"))

        then:
        def e = thrown(RuntimeException)
        e.cause == null
        e.suppressed.find {
            it.message.contains("class $className in $LOADER_NAME") && (it.cause instanceof IOException)
        } != null
    }

    def "entering the scope throws if there is a pending exception"() {
        given:
        TransformErrorHandler handler = handler()
        def outOfScopeClass = "out/of/scope/Class"
        def scopeClass = "some/Class"
        when:
        handler.classLoadingError(outOfScopeClass, new IOException("I/O"))
        handler.enterClassLoadingScope(scopeClass)

        then:
        def e = thrown(ClassNotFoundException)
        e.message.contains("class $scopeClass in $LOADER_NAME")
        e.cause != null && e.cause.message.contains("class $outOfScopeClass in $LOADER_NAME")
    }

    private static TransformErrorHandler handler() {
        return new TransformErrorHandler(LOADER_NAME)
    }
}
