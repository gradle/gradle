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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;

class ProgressListenerToProgressLoggerAdapter implements ProgressListener {
    private final ProgressLoggerFactory progressLoggerFactory;
    LinkedList<String> stack;
    Deque<ProgressLogger> loggerStack;

    public ProgressListenerToProgressLoggerAdapter(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
        stack = new LinkedList<String>();
        loggerStack = new ArrayDeque<ProgressLogger>();
    }

    @Override
    public void statusChanged(ProgressEvent event) {
        String description = event.getDescription();
        if (description.equals("") || stack.size() >= 2 && stack.get(1).equals(description)) {
            loggerStack.pop().completed();
            stack.removeFirst();
            return;
        }
        stack.addFirst(description);
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(ProgressListenerToProgressLoggerAdapter.class);
        progressLogger.start(description, description);
        loggerStack.push(progressLogger);
    }
}
