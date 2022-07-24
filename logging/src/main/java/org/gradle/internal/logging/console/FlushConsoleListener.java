/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.UpdateNowEvent;

/**
 * Flushes the console output. Used when no other listener in the chain flushes the console.
 */
public class FlushConsoleListener implements OutputEventListener {
    private final OutputEventListener delegate;
    private final Console console;

    public FlushConsoleListener(Console console, OutputEventListener delegate) {
        this.delegate = delegate;
        this.console = console;
    }

    @Override
    public void onOutput(OutputEvent event) {
        delegate.onOutput(event);
        if (event instanceof UpdateNowEvent || event instanceof EndOutputEvent) {
            console.flush();
        }
    }
}
