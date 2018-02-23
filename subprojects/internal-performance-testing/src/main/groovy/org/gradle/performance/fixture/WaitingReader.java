/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance.fixture;

import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * When there are no more lines to read from the source reader, this implementation waits until a full new line of content is available.
 * We need this kind of thing because when gc log is used by a forked process (e.g. the daemon) there is a delay
 * between a) forked process has finished and b) the gc log information has the final heap information.
 * The gc log can be written out in chunks (especially if a big GC is happening) which means that we have to wait
 * for a full line to be available instead of just any content before returning from readLine().
 */
public class WaitingReader {

    private static final int READAHEAD_BUFFER_SIZE = 512 * 1024;
    private static final int EOF = -1;
    private static final char NEW_LINE = '\n';
    private static final char CARRIAGE_RETURN = '\r';
    private final BufferedReader reader;
    private final int timeoutMs;
    private final int clockTick;

    //for testing
    int retriedCount;

    public WaitingReader(BufferedReader reader) {
        this(reader, 5000, 200);
    }

    public WaitingReader(BufferedReader reader, int timeoutMs, int clockTick) {
        this.reader = reader;
        this.timeoutMs = timeoutMs;
        this.clockTick = clockTick;
    }

    String readLine() throws IOException {
        CountdownTimer timer = Time.startCountdownTimer(timeoutMs);
        reader.mark(READAHEAD_BUFFER_SIZE);
        int character = EOF;
        while (character != NEW_LINE && character != CARRIAGE_RETURN) {
            character = reader.read();
            if (character == EOF) {
                if (timer.hasExpired()) {
                    break;
                }
                try {
                    Thread.sleep(clockTick);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                retriedCount++;
            }
        }
        reader.reset();
        String line = reader.readLine();
        return line;
    }
}
