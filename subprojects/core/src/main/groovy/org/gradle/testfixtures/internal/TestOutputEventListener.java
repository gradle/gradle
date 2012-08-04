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

package org.gradle.testfixtures.internal;

import com.google.common.collect.Lists;

import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;

import java.util.List;

public class TestOutputEventListener implements OutputEventListener {
    private final List<OutputEvent> events = Lists.newArrayList();

    public void onOutput(OutputEvent event) {
        events.add(event);
    }

    public List<OutputEvent> getEvents() {
        return events;
    }
}
