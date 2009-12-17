/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.listener;

import org.junit.Test;

import java.io.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class EventTest {
    @Test
    public void replacesUnserializableExceptionWithPlaceholder() throws Exception {
        RuntimeException cause = new RuntimeException("nested");
        UnserializableException original = new UnserializableException("message", cause);
        Object transported = transport(original);

        assertThat(transported, instanceOf(RuntimeException.class));
        RuntimeException e = (RuntimeException) transported;
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
        Throwable e = ((RuntimeException) transported).getCause();
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
        RuntimeException e = (RuntimeException) transported;
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
        Throwable e = ((RuntimeException) transported).getCause();
        assertThat(e.getMessage(), equalTo(UndeserializableException.class.getName() + ": " + original.getMessage()));
        assertThat(e.getStackTrace(), equalTo(original.getStackTrace()));

        assertThat(e.getCause().getClass(), equalTo((Object) RuntimeException.class));
        assertThat(e.getCause().getMessage(), equalTo("nested"));
        assertThat(e.getCause().getStackTrace(), equalTo(cause.getStackTrace()));
    }

    private Object transport(Object arg) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new Event(String.class.getMethod("length"), new Object[]{arg}).send(outputStream);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        Event event = Event.receive(inputStream);
        return event.getArguments()[0];
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
