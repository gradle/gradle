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

package org.gradle.internal.filewatch

import org.gradle.api.Action
import org.gradle.api.internal.file.FileSystemSubset
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.events.LogEvent
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.spockframework.lang.ConditionBlock
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

abstract class AbstractFileWatcherTest extends Specification {
    @Rule
    ConfigureLogging logging = new ConfigureLogging({
        if (it instanceof LogEvent) {
            println "[${it.timestamp}] ${it}"
            it.throwable?.printStackTrace()
        }
    })

    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    long waitForEventsMillis = OperatingSystem.current().isMacOsX() ? 6000L : 3500L

    FileSystemSubset fileSystemSubset

    Throwable thrownInWatchExecution
    Action<? super Throwable> onError = {
        thrownInWatchExecution = it
    }

    void cleanup() {
        if (thrownInWatchExecution) {
            throw new ExecutionException("Exception was catched in executing watch", thrownInWatchExecution)
        }
    }

    protected void waitOn(CountDownLatch latch, boolean checkLatch = true) {
        latch.await(waitForEventsMillis, TimeUnit.MILLISECONDS)
        sleep(100)
        if (checkLatch) {
            assert latch.count == 0: "CountDownLatch didn't count down to zero within $waitForEventsMillis ms"
        }
    }

    @ConditionBlock
    protected void await(Closure<?> closure) {
        ConcurrentTestUtil.poll(waitForEventsMillis / 1000 as int, closure)
    }

    protected <T> BlockingVariable<T> blockingVar() {
        new BlockingVariable<T>(waitForEventsMillis / 1000)
    }
}
