/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.internal.tasks.testing.processors.DefaultStandardOutputRedirector;
import org.gradle.internal.logging.StandardOutputCapture;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Some hackery to get JUL output redirected to test output
 */
public class JULRedirector extends DefaultStandardOutputRedirector {
    private boolean reset;

    @Override
    public StandardOutputCapture start() {
        super.start();
        if (!reset) {
            LogManager.getLogManager().reset();
            try {
                LogManager.getLogManager().readConfiguration();
            } catch (IOException error) {
                Logger.getLogger("").addHandler(new ConsoleHandler());
            }
            reset = true;
        }
        return this;
    }
}
