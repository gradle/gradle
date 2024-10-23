/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures.executer;

import java.io.PipedOutputStream;

public interface GradleHandle {
    /**
     * Returns the stream for writing to stdin.
     */
    PipedOutputStream getStdinPipe();

    /**
     * Returns the stdout output currently received from the build. This is live.
     */
    String getStandardOutput();

    /**
     * Returns the stderr output currently received from the build. This is live.
     */
    String getErrorOutput();

    /**
     * Forcefully kills the build and returns immediately. Does not block until the build has finished.
     */
    GradleHandle abort();

    /**
     * Cancel a build that was started as a cancellable build by closing stdin.  Does not block until the build has finished.
     */
    GradleHandle cancel();

    /**
     * Cancel a build that was started as a cancellable build by sending EOT (ctrl-d).  Does not block until the build has finished.
     */
    GradleHandle cancelWithEOT();

    /**
     * Blocks until the build is complete and assert that the build completed successfully.
     */
    ExecutionResult waitForFinish();

    /**
     * Blocks until the build is complete and assert that the build completed with a failure.
     */
    ExecutionFailure waitForFailure();

    /**
     * Blocks until the build is complete and exits, disregarding the result.
     */
    void waitForExit();

    /**
     * Returns true if the build is currently running.
     */
    boolean isRunning();

}
