/*
 * Copyright 2016 the original author or authors.
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

import groovy.transform.CompileStatic
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.DefaultMultiCauseException
import spock.lang.Issue
import spock.lang.Specification

class MessageTest extends Specification {
    GroovyClassLoader source = new GroovyClassLoader(getClass().classLoader)
    GroovyClassLoader dest = new GroovyClassLoader(getClass().classLoader)

    def "can transport graph of exceptions"() {
        def cause1 = new ExceptionWithState("nested-1", ["a", 1])
        def cause2 = new IOException("nested-2")
        def cause = new DefaultMultiCauseException("nested", cause1, cause2)
        def original = new ExceptionWithExceptionField("message", cause)

        when:
        def transported = transport(new TestPayloadMessage(payload: original))

        then:
        transported.payload.class == ExceptionWithExceptionField
        transported.payload.message == "message"

        and:
        transported.payload.throwable.class == DefaultMultiCauseException
        transported.payload.throwable.message == "nested"

        and:
        transported.payload.throwable == transported.payload.cause

        and:
        transported.payload.throwable.causes.size() == 2
        transported.payload.throwable.causes*.class == [ExceptionWithState, IOException]
        transported.payload.throwable.causes*.message == ["nested-1", "nested-2"]

        and:
        transported.payload.throwable.causes[0].values == ["a", 1]
    }

    def "can transport exception with custom serialization"() {
        def cause = new IOException("nested")
        def original = new ExceptionWithCustomSerialization("message", cause)

        when:
        def transported = transport(original)

        then:
        transported.class == ExceptionWithCustomSerialization
        transported.message == "message"

        and:
        transported.throwable.class == IOException
        transported.throwable.message == "nested"
    }

    def "replaces exception with broken writeObject() method with placeholder"() {
        def cause = new RuntimeException("nested")
        def original = new BrokenWriteObjectException("message", cause)

        when:
        def transported = transport(original)

        then:
        looksLike original, transported

        and:
        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.getStackTrace()
    }

    def "replaces exception with field that cannot be serialized with placeholder"() {
        def cause = new RuntimeException("nested")
        def original = new ExceptionWithNonSerializableField("message", cause)

        when:
        def transported = transport(original)

        then:
        looksLike original, transported

        and:
        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.getStackTrace()
    }

    def "replaces nested unserializable exception with placeholder"() {
        def cause = new IOException("nested")
        def original = new BrokenWriteObjectException("message", cause)
        def outer = new RuntimeException("message", original)

        when:
        def transported = transport(outer)

        then:
        transported instanceof RuntimeException
        transported.class == RuntimeException.class
        transported.message == "message"
        transported.stackTrace == outer.stackTrace

        and:
        looksLike original, transported.cause

        and:
        transported.cause.cause.class == IOException
        transported.cause.cause.message == "nested"
        transported.cause.cause.stackTrace == cause.stackTrace
    }

    def "replaces undeserializable exception with placeholder"() {
        def cause = new RuntimeException("nested")
        def original = new BrokenReadObjectException("message", cause)

        when:
        def transported = transport(original)

        then:
        looksLike original, transported

        and:
        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.stackTrace
    }

    def "replaces nested undeserializable exception with placeholder"() {
        def cause = new RuntimeException("nested")
        def original = new BrokenReadObjectException("message", cause)
        def outer = new RuntimeException("message", original)

        when:
        def transported = transport(outer)

        then:
        transported instanceof RuntimeException
        transported.class == RuntimeException
        transported.message == "message"
        transported.stackTrace == outer.stackTrace

        and:
        looksLike original, transported.cause

        and:
        transported.cause.cause.class == RuntimeException.class
        transported.cause.cause.message == "nested"
        transported.cause.cause.stackTrace == cause.stackTrace
    }

    def "replaces unserializable exception field with placeholder"() {
        def cause = new RuntimeException()
        def original = new BrokenReadObjectException("message", cause)
        def outer = new ExceptionWithExceptionField("nested", original)

        when:
        def transported = transport(outer)

        then:
        transported instanceof ExceptionWithExceptionField

        and:
        looksLike original, transported.throwable

        and:
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
        looksLike original, transported

        and:
        transported.cause.class == RuntimeException.class
        transported.cause.message == "nested"
        transported.cause.stackTrace == cause.stackTrace
    }

    def "transports exception with broken methods"() {
        def broken = new CompletelyBrokenException()

        when:
        def transported = transport(broken)

        then:
        transported.class == CompletelyBrokenException
    }

    def "transports unserializable exception with broken methods"() {
        def broken = new CompletelyBrokenException() {
            def Object o = new Object()
        }

        when:
        def transported = transport(broken)

        then:
        transported.class == PlaceholderException
        transported.cause == null
        transported.stackTrace.length == 0

        when:
        transported.message

        then:
        RuntimeException e = thrown()
        e.message == 'broken getMessage()'

        when:
        transported.toString()

        then:
        RuntimeException e2 = thrown()
        e2.message == 'broken toString()'
    }

    @Issue("https://github.com/gradle/gradle/issues/1618")
    def "transports mock exception with null cause"() {
        def exceptionWithNullCause = Mock(Exception)
        exceptionWithNullCause.stackTrace >> null

        when:
        def transported = transport(exceptionWithNullCause)

        then:
        transported.stackTrace == [] as StackTraceElement[]
    }

    @Issue("GRADLE-1996")
    def "can transport exception that implements writeReplace()"() {
        def original = new WriteReplaceException("original")

        when:
        def transported = transport(original)

        then:
        transported instanceof WriteReplaceException
        transported.message == "replaced"
    }

    def "can transport exception that implements readResolve()"() {
        def original = new ReadReplaceException()

        when:
        def transported = transport(original)

        then:
        transported == ReadReplaceException.singleton
    }

    def "can transport broken multicause exception"() {
        def ok = new RuntimeException("broken 1")
        def notOk = new ExceptionWithNonSerializableField("broken 2", new RuntimeException("broken 3"))
        def original = new MultiCauseExceptionWithExceptionField("original", notOk, [ok, notOk])

        when:
        def transported = transport(original)

        then:
        transported instanceof DefaultMultiCauseException
        transported.message == "original"
        transported.causes.size() == 2
        transported.causes[0].message == "broken 1"
        transported.causes[1].message == "broken 2"
        transported.causes[1].cause.message == "broken 3"
    }

    def "retains @Contextual annotation on placeholder"() {
        def notOk = new ContextualExceptionWithNonSerializableField("broken 2", new RuntimeException("broken 3"))

        when:
        def transported = transport(notOk)

        then:
        transported instanceof ContextualPlaceholderException
        transported.class.getAnnotation(Contextual) != null
        looksLike(notOk, transported)
    }

    def "preserves assertion errors"() {
        def assertionError = new CustomAssertionError("true == false")

        when:
        def transported = transport(assertionError)

        then:
        transported instanceof PlaceholderAssertionError
        looksLike(assertionError, transported)
    }

    def "preserves nested assertion errors"() {
        def assertionError = new CustomAssertionError("Boom", new CustomAssertionError("Boom cause!"))

        when:
        def transported = transport(assertionError)

        then:
        transported instanceof PlaceholderAssertionError
        looksLike(assertionError, transported)
        looksLike(assertionError.cause, transported.cause)
    }

    void looksLike(Throwable original, Throwable transported) {
        assert transported instanceof PlaceholderExceptionSupport
        assert transported.exceptionClassName == original.class.name
        assert transported.message == original.message
        assert transported.toString() == original.toString()
        assert transported.stackTrace == original.stackTrace
        assert (transported.class.getAnnotation(Contextual) != null) == (original.class.getAnnotation(Contextual) != null)
    }

    private Object transport(Object arg) {
        def outputStream = new ByteArrayOutputStream()
        Message.send(new TestPayloadMessage(payload: arg), outputStream)

        def inputStream = new ByteArrayInputStream(outputStream.toByteArray())
        def message = Message.receive(inputStream, dest)
        return message.payload
    }

    static class TestPayloadMessage implements Serializable {
        def payload
    }

    static class ExceptionWithNonSerializableField extends RuntimeException {
        def canNotSerialize = new Object()

        ExceptionWithNonSerializableField(String message, Throwable cause) {
            super(message, cause)
        }
    }

    @Contextual
    static class ContextualExceptionWithNonSerializableField extends ExceptionWithNonSerializableField {
        ContextualExceptionWithNonSerializableField(String message, Throwable cause) {
            super(message, cause)
        }
    }

    static class ExceptionWithExceptionField extends RuntimeException {
        Throwable throwable

        ExceptionWithExceptionField(String message, Throwable cause) {
            super(message, cause)
            throwable = cause
        }
    }

    static class MultiCauseExceptionWithExceptionField extends DefaultMultiCauseException {
        Throwable throwable

        MultiCauseExceptionWithExceptionField(String message, Throwable field, List<Throwable> causes) {
            super(message, causes)
            throwable = field
        }
    }

    static class ExceptionWithState extends RuntimeException {
        List<?> values

        ExceptionWithState(String message, List<?> values) {
            super(message)
            this.values = values
        }
    }

    static class BrokenWriteObjectException extends RuntimeException {
        BrokenWriteObjectException(String message, Throwable cause) {
            super(message, cause)
        }

        private void writeObject(ObjectOutputStream outstr) throws IOException {
            outstr.writeObject(new Object())
        }
    }

    static class CompletelyBrokenException extends RuntimeException {
        @Override
        String getMessage() {
            throw new RuntimeException("broken getMessage()", null);
        }

        @Override
        public String toString() {
            throw new RuntimeException("broken toString()", null);
        }

        @Override
        public Throwable getCause() {
            throw new RuntimeException("broken getCause()", null);
        }

        @Override
        StackTraceElement[] getStackTrace() {
            throw new RuntimeException("broken getStackTrace()", null);
        }
    }

    static class BrokenReadObjectException extends RuntimeException {
        BrokenReadObjectException(String message, Throwable cause) {
            super(message, cause)
        }

        private void readObject(ObjectInputStream outstr) {
            throw new RuntimeException("broken readObject()")
        }
    }

    static class ExceptionWithCustomSerialization extends RuntimeException {
        def transient throwable

        ExceptionWithCustomSerialization(String message, Throwable cause) {
            super(message)
            throwable = cause
        }

        private void writeObject(ObjectOutputStream outstr) {
            outstr.defaultWriteObject()
            outstr.writeObject(throwable)
        }

        private void readObject(ObjectInputStream instr) {
            instr.defaultReadObject()
            throwable = instr.readObject()
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

    static class ReadReplaceException extends Exception {
        static Exception singleton = new Exception("replaced")

        private Object readResolve() {
            return singleton
        }
    }

    @CompileStatic
    static class CustomAssertionError extends AssertionError {
        CustomAssertionError(Object message) {
            super(message)
        }

        CustomAssertionError(String message, Throwable cause) {
            super(message, cause)
        }

        private void readObject(ObjectInputStream outstr) {
            throw new RuntimeException("broken readObject()")
        }
    }
}

