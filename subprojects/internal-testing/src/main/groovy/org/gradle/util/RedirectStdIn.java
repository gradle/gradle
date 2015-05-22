/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * A Junit rule which restores System.in at the end of the test.
 *
 * Provides a pipe for providing input to System.in in the tests
 */
public class RedirectStdIn implements TestRule {
    private PipedInputStream emulatedSystemIn = new PipedInputStream();
    private PipedOutputStream stdinPipe;

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final InputStream originalStdIn = System.in;
                initPipe();
                System.setIn(emulatedSystemIn);
                try {
                    base.evaluate();
                } finally {
                    System.setIn(originalStdIn);
                    closePipe();
                }
            }
        };
    }

    public PipedOutputStream getStdinPipe() {
        initPipe();
        return stdinPipe;
    }

    private void initPipe() {
        if (stdinPipe == null) {
            emulatedSystemIn = new PipedInputStream();
            try {
                stdinPipe = new PipedOutputStream(emulatedSystemIn);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    public void resetStdinPipe() {
        closePipe();
        initPipe();
        System.setIn(emulatedSystemIn);
    }

    private void closePipe() {
        CompositeStoppable.stoppable(stdinPipe, emulatedSystemIn).stop();
        stdinPipe = null;
        emulatedSystemIn = null;
    }
}
