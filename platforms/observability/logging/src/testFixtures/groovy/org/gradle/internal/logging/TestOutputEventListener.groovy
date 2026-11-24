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

package org.gradle.internal.logging

import groovy.transform.CompileStatic
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.StyledTextOutputEvent

@CompileStatic
class TestOutputEventListener implements OutputEventListener {
    final StringWriter writer = new StringWriter()

    @Override
    String toString() {
        return writer.toString()
    }

    void reset() {
        writer
    }

    @Override
    synchronized void onOutput(OutputEvent event) {
        if(event instanceof LogEvent){
            LogEvent logEvent = event as LogEvent
            writer.append("[")
            writer.append(logEvent.toString())
            writer.append("]")
        }
        if(event instanceof StyledTextOutputEvent){
            StyledTextOutputEvent styledTextOutputEvent = event as StyledTextOutputEvent
            writer.append("[")
            writer.append(styledTextOutputEvent.toString())
            writer.append("]")
        }
    }
}
