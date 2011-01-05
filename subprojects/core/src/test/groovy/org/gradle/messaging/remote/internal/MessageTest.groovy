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
    private final GroovyClassLoader source = new GroovyClassLoader(getClass().getClassLoader());
    private final GroovyClassLoader dest = new GroovyClassLoader(getClass().getClassLoader());

    def replacesUnserializableExceptionWithPlaceholder() {
        RuntimeException cause = new RuntimeException("nested");
        UnserializableException original = new UnserializableException("message", cause);

        when:
        Object transported = transport(original);

        then:
        transported instanceof PlaceholderException
        transported.message == UnserializableException.class.name + ": " + original.message
        transported.stackTrace == original.stackTrace

        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.getStackTrace()
    }

    def replacesNestedUnserializableExceptionWithPlaceholder() {
        Exception cause = new IOException("nested");
        UnserializableException original = new UnserializableException("message", cause);
        RuntimeException outer = new RuntimeException('message', original)

        when:
        Object transported = transport(outer);

        then:
        transported.class == RuntimeException.class
        transported.message == 'message'
        transported.stackTrace == outer.stackTrace

        transported.cause instanceof PlaceholderException
        transported.cause.message == UnserializableException.class.name + ": " + original.message
        transported.cause.stackTrace == original.stackTrace

        transported.cause.cause.class == IOException
        transported.cause.cause.message == "nested"
        transported.cause.cause.stackTrace == cause.stackTrace
    }

    def replacesUndeserializableExceptionWithPlaceholder() {
        RuntimeException cause = new RuntimeException("nested");
        UndeserializableException original = new UndeserializableException("message", cause);

        when:
        Object transported = transport(original);

        then:
        transported instanceof PlaceholderException
        transported.message == UndeserializableException.class.name + ": " + original.message
        transported.stackTrace == original.stackTrace

        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.stackTrace
    }

    def replacesNestedUndeserializableExceptionWithPlaceholder() {
        RuntimeException cause = new RuntimeException("nested");
        UndeserializableException original = new UndeserializableException("message", cause);
        RuntimeException outer = new RuntimeException('message', original)

        when:
        Object transported = transport(outer);

        then:
        transported.class == RuntimeException
        transported.message == 'message'
        transported.stackTrace == outer.stackTrace

        transported.cause instanceof PlaceholderException
        transported.cause.message == UndeserializableException.class.name + ": " + original.message
        transported.cause.stackTrace == original.stackTrace

        transported.cause.cause.class == RuntimeException.class
        transported.cause.cause.message == "nested"
        transported.cause.cause.stackTrace == cause.stackTrace
    }

    def replacesUnserializableExceptionFieldWithPlaceholder() {
        RuntimeException cause = new RuntimeException()
        UndeserializableException original = new UndeserializableException("message", cause);
        ExceptionWithExceptionField outer = new ExceptionWithExceptionField("nested", original)

        when:
        Object transported = transport(outer);

        then:
        transported instanceof ExceptionWithExceptionField

        transported.throwable instanceof PlaceholderException
        transported.throwable.message == UndeserializableException.class.name + ": " + original.message
        transported.throwable.stackTrace == original.stackTrace

        transported.throwable == transported.cause
    }

    def replacesIncompatibleExceptionWithLocalVersion() {
        RuntimeException cause = new RuntimeException("nested");
        Class<? extends RuntimeException> sourceExceptionType = source.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { public TestException(String msg, Throwable cause) { super(msg, cause); } }");
        Class<? extends RuntimeException> destExceptionType = dest.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { private String someField; public TestException(String msg) { super(msg); } }");

        RuntimeException original = sourceExceptionType.newInstance("message", cause);

        when:
        RuntimeException transported = transport(original);

        then:
        transported.class == destExceptionType
        transported.message == original.message
        transported.stackTrace == original.stackTrace

        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.stackTrace
    }

    def usesPlaceholderWhenLocalExceptionCannotBeConstructed() {
        RuntimeException cause = new RuntimeException("nested");
        Class<? extends RuntimeException> sourceExceptionType = source.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { public TestException(String msg, Throwable cause) { super(msg, cause); } }");
        dest.parseClass("package org.gradle; public class TestException extends RuntimeException { private String someField; }");

        RuntimeException original = sourceExceptionType.newInstance("message", cause);

        when:
        Object transported = transport(original);

        then:
        transported  instanceof PlaceholderException
        transported.message == original.toString()
        transported.stackTrace == original.stackTrace

        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.stackTrace
    }

    private Object transport(Object arg) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Message.send(new TestPayloadMessage(payload: arg), outputStream);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        def message = Message.receive(inputStream, dest);
        return message.payload
    }
}

private class TestPayloadMessage extends Message {
    def payload
}

private class ExceptionWithExceptionField extends RuntimeException {
    def Throwable throwable

    def ExceptionWithExceptionField(String message, Throwable cause) {
        super(message, cause)
        throwable = cause
    }
}

private class UnserializableException extends RuntimeException {
    public UnserializableException(String message, Throwable cause) {
        super(message, cause);
    }

    private void writeObject(ObjectOutputStream outstr) throws IOException {
        outstr.writeObject(new Object());
    }
}

private class UndeserializableException extends RuntimeException {
    public UndeserializableException(String message, Throwable cause) {
        super(message, cause);
    }

    private void readObject(ObjectInputStream outstr) throws ClassNotFoundException {
        throw new ClassNotFoundException();
    }
}
