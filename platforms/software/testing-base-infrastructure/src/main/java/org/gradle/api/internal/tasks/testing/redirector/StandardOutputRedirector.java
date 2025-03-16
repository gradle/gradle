/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.redirector;

public interface StandardOutputRedirector {

    /**
     * Starts redirection of System.out and System.err to the listener attached to
     * {@link #redirectStandardErrorTo(OutputListener)} and
     * {@link #redirectStandardOutputTo(OutputListener)}
     */
    void start();

    /**
     * Restores System.out and System.err to the values they had before {@link #start()} has been called.
     */
    void stop();

    void redirectStandardOutputTo(OutputListener stdOutDestination);

    void redirectStandardErrorTo(OutputListener stdErrDestination);

    interface OutputListener {
        void onOutput(CharSequence output);
    }
}
