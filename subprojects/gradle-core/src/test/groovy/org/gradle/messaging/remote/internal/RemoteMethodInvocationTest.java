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

package org.gradle.messaging.remote.internal;

import groovy.lang.GroovyClassLoader;
import org.junit.Test;

import java.io.*;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class RemoteMethodInvocationTest {
    private final GroovyClassLoader source = new GroovyClassLoader(getClass().getClassLoader());
    private final GroovyClassLoader dest = new GroovyClassLoader(getClass().getClassLoader());

    @Test
    public void equalsAndHashCode() throws Exception {
        RemoteMethodInvocation invocation = new RemoteMethodInvocation(1, new Object[]{"param"});
        RemoteMethodInvocation equalInvocation = new RemoteMethodInvocation(1, new Object[]{"param"});
        RemoteMethodInvocation differentMethod = new RemoteMethodInvocation(2, new Object[]{"param"});
        RemoteMethodInvocation differentArgs = new RemoteMethodInvocation(1, new Object[]{"a", "b"});
        assertThat(invocation, strictlyEqual(equalInvocation));
        assertThat(invocation, not(equalTo(differentMethod)));
        assertThat(invocation, not(equalTo(differentArgs)));
    }

    @Test
    public void replacesUnserializableExceptionWithPlaceholder() throws Exception {
        RuntimeException cause = new RuntimeException("nested");
        UnserializableException original = new UnserializableException("message", cause);
        Object transported = transport(original);

        assertThat(transported, instanceOf(PlaceholderException.class));
        PlaceholderException e = (PlaceholderException) transported;
        assertThat(e.getMessage(), equalTo(UnserializableException.class.getName() + ": " + original.getMessage()));
        assertThat(e.getStackTrace(), equalTo(original.getStackTrace()));

        assertThat(e.getCause().getClass(), equalTo((Object) RuntimeException.class));
        assertThat(e.getCause().getMessage(), equalTo("nested"));
        assertThat(e.getCause().getStackTrace(), equalTo(cause.getStackTrace()));
    }

    @Test
    public void replacesNestedUnserializableExceptionWithPlaceholder() throws Exception {
        Exception cause = new IOException("nested");
        UnserializableException original = new UnserializableException("message", cause);
        Object transported = transport(new RuntimeException(original));

        assertThat(transported, instanceOf(RuntimeException.class));
        PlaceholderException e = (PlaceholderException) ((RuntimeException) transported).getCause();
        assertThat(e.getMessage(), equalTo(UnserializableException.class.getName() + ": " + original.getMessage()));
        assertThat(e.getStackTrace(), equalTo(original.getStackTrace()));

        assertThat(e.getCause().getClass(), equalTo((Object) IOException.class));
        assertThat(e.getCause().getMessage(), equalTo("nested"));
        assertThat(e.getCause().getStackTrace(), equalTo(cause.getStackTrace()));
    }

    @Test
    public void replacesUndeserializableExceptionWithPlaceholder() throws Exception {
        RuntimeException cause = new RuntimeException("nested");
        UndeserializableException original = new UndeserializableException("message", cause);
        Object transported = transport(original);

        assertThat(transported, instanceOf(RuntimeException.class));
        PlaceholderException e = (PlaceholderException) transported;
        assertThat(e.getMessage(), equalTo(UndeserializableException.class.getName() + ": " + original.getMessage()));
        assertThat(e.getStackTrace(), equalTo(original.getStackTrace()));

        assertThat(e.getCause().getClass(), equalTo((Object) RuntimeException.class));
        assertThat(e.getCause().getMessage(), equalTo("nested"));
        assertThat(e.getCause().getStackTrace(), equalTo(cause.getStackTrace()));
    }

    @Test
    public void replacesNestedUndeserializableExceptionWithPlaceholder() throws Exception {
        RuntimeException cause = new RuntimeException("nested");
        UndeserializableException original = new UndeserializableException("message", cause);
        Object transported = transport(new RuntimeException(original));

        assertThat(transported, instanceOf(RuntimeException.class));
        PlaceholderException e = (PlaceholderException) ((RuntimeException) transported).getCause();
        assertThat(e.getMessage(), equalTo(UndeserializableException.class.getName() + ": " + original.getMessage()));
        assertThat(e.getStackTrace(), equalTo(original.getStackTrace()));

        assertThat(e.getCause().getClass(), equalTo((Object) RuntimeException.class));
        assertThat(e.getCause().getMessage(), equalTo("nested"));
        assertThat(e.getCause().getStackTrace(), equalTo(cause.getStackTrace()));
    }

    @Test
    public void replacesIncompatibleExceptionWithLocalVersion() throws Exception {
        RuntimeException cause = new RuntimeException("nested");
        Class<? extends RuntimeException> sourceExceptionType = source.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { public TestException(String msg, Throwable cause) { super(msg, cause); } }");
        Class<? extends RuntimeException> destExceptionType = dest.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { private String someField; public TestException(String msg) { super(msg); } }");

        RuntimeException original = sourceExceptionType.getConstructor(String.class, Throwable.class).newInstance("message", cause);
        Object transported = transport(original);

        assertThat(transported, instanceOf(destExceptionType));
        RuntimeException e = (RuntimeException) transported;
        assertThat(e.getMessage(), equalTo(original.getMessage()));
        assertThat(e.getStackTrace(), equalTo(original.getStackTrace()));

        assertThat(e.getCause().getClass(), equalTo((Object) RuntimeException.class));
        assertThat(e.getCause().getMessage(), equalTo("nested"));
        assertThat(e.getCause().getStackTrace(), equalTo(cause.getStackTrace()));
    }

    @Test
    public void usesPlaceholderWhenLocalExceptionCannotBeConstructed() throws Exception {
        RuntimeException cause = new RuntimeException("nested");
        Class<? extends RuntimeException> sourceExceptionType = source.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { public TestException(String msg, Throwable cause) { super(msg, cause); } }");
        Class<? extends RuntimeException> destExceptionType = dest.parseClass(
                "package org.gradle; public class TestException extends RuntimeException { private String someField; }");

        RuntimeException original = sourceExceptionType.getConstructor(String.class, Throwable.class).newInstance("message", cause);
        Object transported = transport(original);

        assertThat(transported, instanceOf(PlaceholderException.class));
        RuntimeException e = (RuntimeException) transported;
        assertThat(e.getMessage(), equalTo(original.toString()));
        assertThat(e.getStackTrace(), equalTo(original.getStackTrace()));

        assertThat(e.getCause().getClass(), equalTo((Object) RuntimeException.class));
        assertThat(e.getCause().getMessage(), equalTo("nested"));
        assertThat(e.getCause().getStackTrace(), equalTo(cause.getStackTrace()));
    }

    private Object transport(Object arg) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new RemoteMethodInvocation(1, new Object[]{arg}).send(outputStream);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        RemoteMethodInvocation invocation = (RemoteMethodInvocation) Message.receive(inputStream, dest);
        return invocation.getArguments()[0];
    }

    private static class UnserializableException extends RuntimeException {
        public UnserializableException(String message, Throwable cause) {
            super(message, cause);
        }

        private void writeObject(ObjectOutputStream outstr) throws IOException {
            outstr.writeObject(new Object());
        }
    }

    private static class UndeserializableException extends RuntimeException {
        public UndeserializableException(String message, Throwable cause) {
            super(message, cause);
        }

        private void readObject(ObjectInputStream outstr) throws ClassNotFoundException {
            throw new ClassNotFoundException();
        }
    }

}