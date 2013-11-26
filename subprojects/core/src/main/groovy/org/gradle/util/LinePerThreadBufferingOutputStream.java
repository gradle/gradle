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

package org.gradle.util;

import org.gradle.internal.io.TextStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;

public class LinePerThreadBufferingOutputStream extends PrintStream {
    private final TextStream handler;
    private final ThreadLocal<PrintStream> stream = new ThreadLocal<PrintStream>(){
        @Override
        protected PrintStream initialValue() {
            return AccessController.doPrivileged(new PrivilegedAction<PrintStream>() {
                public PrintStream run() {
                    return new PrintStream(new LineBufferingOutputStream(handler));
                }
            });
        }
    };

    public LinePerThreadBufferingOutputStream(TextStream handler) {
        super(new ByteArrayOutputStream(), true);
        this.handler = handler;
    }

    private PrintStream getStream() {
        return stream.get();
    }

    @Override
    public PrintStream append(CharSequence csq) {
        getStream().append(csq);
        return this;
    }

    @Override
    public PrintStream append(char c) {
        getStream().append(c);
        return this;
    }

    @Override
    public PrintStream append(CharSequence csq, int start, int end) {
        getStream().append(csq, start, end);
        return this;
    }

    @Override
    public boolean checkError() {
        return getStream().checkError();
    }

    @Override
    public void close() {
        getStream().close();
    }

    @Override
    public void flush() {
        getStream().flush();
    }

    @Override
    public PrintStream format(String format, Object... args) {
        getStream().format(format, args);
        return this;
    }

    @Override
    public PrintStream format(Locale l, String format, Object... args) {
        getStream().format(l, format, args);
        return this;
    }

    @Override
    public void print(boolean b) {
        getStream().print(b);
    }

    @Override
    public void print(char c) {
        getStream().print(c);
    }

    @Override
    public void print(double d) {
        getStream().print(d);
    }

    @Override
    public void print(float f) {
        getStream().print(f);
    }

    @Override
    public void print(int i) {
        getStream().print(i);
    }

    @Override
    public void print(long l) {
        getStream().print(l);
    }

    @Override
    public void print(Object obj) {
        getStream().print(obj);
    }

    @Override
    public void print(char[] s) {
        getStream().print(s);
    }

    @Override
    public void print(String s) {
        getStream().print(s);
    }

    @Override
    public PrintStream printf(String format, Object... args) {
        getStream().printf(format, args);
        return this;
    }

    @Override
    public PrintStream printf(Locale l, String format, Object... args) {
        getStream().printf(l, format, args);
        return this;
    }

    @Override
    public void println() {
        getStream().println();
    }

    @Override
    public void println(boolean x) {
        getStream().println(x);
    }

    @Override
    public void println(char x) {
        getStream().println(x);
    }

    @Override
    public void println(char[] x) {
        getStream().println(x);
    }

    @Override
    public void println(double x) {
        getStream().println(x);
    }

    @Override
    public void println(float x) {
        getStream().println(x);
    }

    @Override
    public void println(int x) {
        getStream().println(x);
    }

    @Override
    public void println(long x) {
        getStream().println(x);
    }

    @Override
    public void println(Object x) {
        getStream().println(x);
    }

    @Override
    public void println(String x) {
        getStream().println(x);
    }

    @Override
    public void write(int b) {
        getStream().write(b);
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        getStream().write(buf, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        getStream().write(b);
    }
}
