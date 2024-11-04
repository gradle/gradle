/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.util.internal

import org.junit.Test

import java.util.concurrent.CopyOnWriteArrayList

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class DisconnectableInputStreamTest extends MultithreadedTestRule {
    @Test
    void inputStreamReadsFromSourceInputStream() {
        def instr = new DisconnectableInputStream(stream("some text"), executorFactory.create("test executer"))

        assertReads(instr, "some text")

        def nread = instr.read(new byte[20])
        assertThat(nread, equalTo(-1))

        instr.close()
    }

    @Test
    void buffersDataReadFromSourceInputStream() {
        def instr = new DisconnectableInputStream(stream("test1test2end"), executorFactory.create("test executer"))

        assertReads(instr, "test1")
        assertReads(instr, "test2")
        assertReads(instr, "end")

        def nread = instr.read(new byte[20])
        assertThat(nread, equalTo(-1))

        instr.close()
    }

    @Test
    void canReadSingleChars() {
        def instr = new DisconnectableInputStream(stream("abc"), executorFactory.create("test executer"))

        assertThat((char) instr.read(), equalTo('a'.charAt(0)))
        assertThat((char) instr.read(), equalTo('b'.charAt(0)))
        assertThat((char) instr.read(), equalTo('c'.charAt(0)))
        assertThat(instr.read(), equalTo(-1))

        instr.close()
    }

    @Test
    void canReadUsingZeroLengthBuffer() {
        def instr = new DisconnectableInputStream(stream("abc"), executorFactory.create("test executer"))

        assertThat(instr.read(new byte[0], 0, 0), equalTo(0))
        assertReads(instr, "abc")
        assertThat(instr.read(new byte[0], 0, 0), equalTo(-1))

        instr.close()
    }

    @Test
    void canFillAndEmptyBufferInChunks() {
        def source = stream()
        source.onRead { buffer, pos, count ->
            System.arraycopy('aaaaaa'.bytes, 0, buffer, pos, 6)
            return 6
        }
        source.onRead { buffer, pos, count ->
            syncAt(1)
            syncAt(2)
            System.arraycopy('aaaa'.bytes, 0, buffer, pos, 4)
            return 4
        }
        source.onRead { buffer, pos, count ->
            syncAt(3)
            System.arraycopy('aa'.bytes, 0, buffer, pos, 2)
            syncAt(4)
            return 2
        }

        def instr = new DisconnectableInputStream(source, executorFactory.create("test executer"), 10)

        run {
            syncAt(1)
            assertReads(instr, "aaaa")
            syncAt(2)
            assertReads(instr, "aaaaaa")
            syncAt(3)
            syncAt(4)
            assertReads(instr, "aa")
        }

        instr.close()
    }

    @Test
    void readBlocksUntilDataIsAvailable() {

        def source = stream()
        source.onRead { buffer, pos, count ->
            byte[] expected = "some text".bytes
            System.arraycopy(expected, 0, buffer, pos, expected.length)
            syncAt(1)
            return expected.length
        }

        def instr = new DisconnectableInputStream(source, executorFactory.create("test executer"))
        run {
            expectBlocksUntil(1) {
                assertReads(instr, "some text")
            }
        }

        instr.close()
    }

    @Test
    void readBlocksUntilInputStreamIsClosed() {
        clockTick(1).hasParticipants(3)
        clockTick(2).hasParticipants(3)

        def source = stream()
        source.onRead { buffer, pos, count ->
            syncAt(1)
            syncAt(2)
            return count
        }

        def instr = new DisconnectableInputStream(source, executorFactory.create("test executer"))

        start {
            expectBlocksUntil(1) {
                def nread = instr.read(new byte[20])
                assertThat(nread, equalTo(-1))
            }
            syncAt(2)
        }

        run {
            syncAt(1)
            instr.close()
            syncAt(2)
        }
    }

    @Test
    void readBlocksUntilEndOfInputReached() {
        def source = stream()
        source.onRead { buffer, pos, count ->
            syncAt(1)
            return -1
        }

        def instr = new DisconnectableInputStream(source, executorFactory.create("test executer"))

        run {
            expectBlocksUntil(1) {
                def nread = instr.read(new byte[20])
                assertThat(nread, equalTo(-1))
            }
        }

        instr.close()
    }

    @Test
    void readerBlocksUntilReaderReceivesReadException() {
        IOException failure = new IOException("failed")

        def source = stream()
        source.onRead { buffer, pos, count ->
            throw failure
        }

        def instr = new DisconnectableInputStream(source, executorFactory.create("test executer"))

        run {
            def nread = instr.read(new byte[20])
            assertThat(nread, equalTo(-1))
        }
    }

    @Test
    void readerThreadBlocksWhenBufferFull() {
        def source = stream()
        source.onRead { buffer, pos, count ->
            System.arraycopy('abcdefghij'.bytes, 0, buffer, pos, 10)
            expectLater(1)
            return 10
        }
        source.onRead { buffer, pos, count ->
            shouldBeAt(1)
            return -1
        }

        def instr = new DisconnectableInputStream(source, executorFactory.create("test executer"), 10)

        run {
            syncAt(1)
            assertReads(instr, 'abc')
            assertReads(instr, 'defghi')
            assertReads(instr, 'j')
        }

        instr.close()
    }

    @Test
    void readerThreadStopsReadingAfterClose() {
        def source = stream()
        source.onRead { buffer, pos, count ->
            return count
        }

        def instr = new DisconnectableInputStream(source, executorFactory.create("test executer"))
        instr.read()
        instr.close()

        waitForAll()
    }

    @Test
    void cannotReadFromInputStreamAfterItIsClosed() {
        def instr = new DisconnectableInputStream(stream("some text"), executorFactory.create("test executer"))
        instr.close()

        assertThat(instr.read(), equalTo(-1))
        assertThat(instr.read(new byte[10]), equalTo(-1))
        assertThat(instr.read(new byte[10], 2, 5), equalTo(-1))
    }

    def assertReads(InputStream instr, String expected) {
        def expectedBytes = expected.bytes
        def buffer = new byte[expectedBytes.length]
        def remaining = expectedBytes.length
        while (remaining > 0) {
            def nread = instr.read(buffer, expectedBytes.length - remaining, remaining)
            assertTrue(nread > 0)
            remaining -= nread
        }
        assertThat(new String(buffer), equalTo(expected))
    }

    def stream(String text) {
        return new ByteArrayInputStream(text.bytes)
    }

    def stream() {
        return new ActionInputStream()
    }
}

class ActionInputStream extends InputStream {
    List<Closure> actions = new CopyOnWriteArrayList<Closure>()

    def onRead(Closure cl) {
        actions << cl
    }

    @Override
    int read(byte[] bytes, int pos, int count) {
        if (actions.isEmpty()) {
            return -1
        }
        Closure action = actions.remove(0)
        return action.call([bytes, pos, count])
    }

    @Override
    int read() {
        throw new UnsupportedOperationException()
    }

}
