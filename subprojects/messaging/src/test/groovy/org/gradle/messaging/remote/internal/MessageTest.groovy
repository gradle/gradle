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
import spock.lang.Issue
import spock.lang.Ignore

class MessageTest extends Specification {
    GroovyClassLoader source = new GroovyClassLoader(getClass().classLoader)
    GroovyClassLoader dest = new GroovyClassLoader(getClass().classLoader)

    def "replaces unserializable exception with placeholder"() {
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

    def "replaces nested unserializable exception with placeholder"() {
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

    def "replaces undeserializable exception with placeholder"() {
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

    def "replaces nested undeserializable exception with placeholder"() {
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

    def "replaces unserializable exception field with placeholder"() {
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

    def "replaces incompatible exception with local version"() {
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

    def "uses placeholder when local exception cannot be constructed"() {
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

    def "creates placeholder with toString() behaviour as original"() {
        def cause = new IOException("nested")
        def broken = new SerializableToStringException("message", cause)

        when:
        def transported = transport(broken)
        transported.toString()
        then:

        def toStringException = thrown(RuntimeException)
        toStringException.getMessage() == "broken toString"

        when:
        cause = new IOException("nested")
        broken = new UnserializableToStringException("message", cause)
        transported = transport(broken)
        transported.toString()

        then:
        toStringException = thrown(PlaceholderException)
        toStringException.getMessage() == "broken toString"
    }

    @Ignore
    @Issue("GRADLE-1996")
    def "can transport exception that implements writeReplace()"() {
        def original = new WriteReplaceException("original")

        when:
        def transported = transport(original)

        then:
        noExceptionThrown()
        transported instanceof WriteReplaceException
        transported.message == "replaced"
    }

    private Object transport(Object arg) {
        def outputStream = new ByteArrayOutputStream()
        Message.send(new TestPayloadMessage(payload: arg), outputStream)

        def inputStream = new ByteArrayInputStream(outputStream.toByteArray())
        def message = Message.receive(inputStream, dest)
        return message.payload
    }

    static class TestPayloadMessage extends Message {
        def payload
    }

    static class ExceptionWithExceptionField extends RuntimeException {
        Throwable throwable

        ExceptionWithExceptionField(String message, Throwable cause) {
            super(message, cause)
            throwable = cause
        }
    }

    static class UnserializableException extends RuntimeException {
        UnserializableException(String message, Throwable cause) {
            super(message, cause)
        }

        private void writeObject(ObjectOutputStream outstr) throws IOException {
            outstr.writeObject(new Object())
        }
    }

    static class UnserializableToStringException extends RuntimeException {
        UnserializableToStringException (String message, Throwable cause) {
            super(message, cause)
        }

        public String toString() {
            throw new UnserializableException("broken toString", null);
        }

        private void writeObject(ObjectOutputStream outstr) throws IOException {
            outstr.writeObject(new Object())
        }
    }

    static class SerializableToStringException extends RuntimeException {
        SerializableToStringException(String message, Throwable cause) {
            super(message, cause)
        }

        public String toString() {
            throw new RuntimeException("broken toString", null);
        }
    }

    static class UndeserializableException extends RuntimeException {
        UndeserializableException(String message, Throwable cause) {
            super(message, cause)
        }

        private void readObject(ObjectInputStream outstr) throws ClassNotFoundException {
            throw new ClassNotFoundException()
        }
    }

    static class WriteReplaceException extends Exception {
        WriteReplaceException(String message) {
            super(message)
        }

        private Object writeReplace() {
            return new WriteReplaceException("replaced")
        }
    }
}

