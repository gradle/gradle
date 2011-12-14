/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.remote.internal

import spock.lang.Specification

class MessageTest extends Specification {
    GroovyClassLoader source = new GroovyClassLoader(getClass().classLoader)
    GroovyClassLoader dest = new GroovyClassLoader(getClass().classLoader)

    def replacesUnserializableExceptionWithPlaceholder() {
        def cause = new RuntimeException("nested")
        def original = new UnserializableException("message", cause)

        when:
        def transported = transport(original)

        then:
        transported instanceof PlaceholderException
        transported.message == original.message
        transported.stackTrace == original.stackTrace

        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.getStackTrace()
    }

    def replacesNestedUnserializableExceptionWithPlaceholder() {
        def cause = new IOException("nested")
        def original = new UnserializableException("message", cause)
        def outer = new RuntimeException("message", original)

        when:
        def transported = transport(outer)

        then:
        transported instanceof RuntimeException
        transported.class == RuntimeException.class
        transported.message == "message"
        transported.stackTrace == outer.stackTrace

        transported.cause instanceof PlaceholderException
        transported.cause.message == original.message
        transported.cause.stackTrace == original.stackTrace

        transported.cause.cause.class == IOException
        transported.cause.cause.message == "nested"
        transported.cause.cause.stackTrace == cause.stackTrace
    }

    def replacesUndeserializableExceptionWithPlaceholder() {
        def cause = new RuntimeException("nested")
        def original = new UndeserializableException("message", cause)

        when:
        def transported = transport(original)

        then:
        transported instanceof PlaceholderException
        transported.message == original.message
        transported.stackTrace == original.stackTrace

        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.stackTrace
    }

    def replacesNestedUndeserializableExceptionWithPlaceholder() {
        def cause = new RuntimeException("nested")
        def original = new UndeserializableException("message", cause)
        def outer = new RuntimeException("message", original)

        when:
        def transported = transport(outer)

        then:
        transported instanceof RuntimeException
        transported.class == RuntimeException
        transported.message == "message"
        transported.stackTrace == outer.stackTrace

        transported.cause instanceof PlaceholderException
        transported.cause.message == original.message
        transported.cause.stackTrace == original.stackTrace

        transported.cause.cause.class == RuntimeException.class
        transported.cause.cause.message == "nested"
        transported.cause.cause.stackTrace == cause.stackTrace
    }

    def replacesUnserializableExceptionFieldWithPlaceholder() {
        def cause = new RuntimeException()
        def original = new UndeserializableException("message", cause)
        def outer = new ExceptionWithExceptionField("nested", original)

        when:
        def transported = transport(outer)

        then:
        transported instanceof ExceptionWithExceptionField

        transported.throwable instanceof PlaceholderException
        transported.throwable.message == original.message
        transported.throwable.stackTrace == original.stackTrace

        transported.throwable == transported.cause
    }

    def replacesIncompatibleExceptionWithLocalVersion() {
        def cause = new RuntimeException("nested")
        def sourceExceptionType = source.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { public TestException(String msg, Throwable cause) { super(msg, cause); } }")
        def destExceptionType = dest.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { private String someField; public TestException(String msg) { super(msg); } }")

        def original = sourceExceptionType.newInstance("message", cause)

        when:
        def transported = transport(original)

        then:
        transported instanceof RuntimeException
        transported.class == destExceptionType
        transported.message == original.message
        transported.stackTrace == original.stackTrace

        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.stackTrace
    }

    def usesPlaceholderWhenLocalExceptionCannotBeConstructed() {
        def cause = new RuntimeException("nested")
        def sourceExceptionType = source.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { public TestException(String msg, Throwable cause) { super(msg, cause); } }")
        dest.parseClass("package org.gradle; public class TestException extends RuntimeException { private String someField; }")

        def original = sourceExceptionType.newInstance("message", cause)

        when:
        def transported = transport(original)

        then:
        transported instanceof PlaceholderException
        transported.message == original.message
        transported.stackTrace == original.stackTrace

        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.stackTrace
    }

    private Object transport(Object arg) {
        def outputStream = new ByteArrayOutputStream()
        Message.send(new TestPayloadMessage(payload: arg), outputStream)

        def inputStream = new ByteArrayInputStream(outputStream.toByteArray())
        def message = Message.receive(inputStream, dest)
        return message.payload
    }
}

private class TestPayloadMessage extends Message {
    def payload
}

private class ExceptionWithExceptionField extends RuntimeException {
    Throwable throwable

    ExceptionWithExceptionField(String message, Throwable cause) {
        super(message, cause)
        throwable = cause
    }
}

private class UnserializableException extends RuntimeException {
    UnserializableException(String message, Throwable cause) {
        super(message, cause)
    }

    private void writeObject(ObjectOutputStream outstr) throws IOException {
        outstr.writeObject(new Object())
    }
}

private class UndeserializableException extends RuntimeException {
    UndeserializableException(String message, Throwable cause) {
        super(message, cause)
    }

    private void readObject(ObjectInputStream outstr) throws ClassNotFoundException {
        throw new ClassNotFoundException()
    }
}
