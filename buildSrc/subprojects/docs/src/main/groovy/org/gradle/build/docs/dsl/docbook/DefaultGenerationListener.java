/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.docbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public class DefaultGenerationListener implements GenerationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGenerationListener.class);
    private final LinkedList<String> contextStack = new LinkedList<String>();

    @Override
    public void warning(String message) {
        LOGGER.warn(String.format("%s: %s", contextStack.getFirst(), message));
    }

    @Override
    public void start(String context) {
        contextStack.addFirst(context);
    }

    @Override
    public void finish() {
        contextStack.removeFirst();
    }
}
