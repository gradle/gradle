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

package org.gradle.logging.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.nativeplatform.NativeIntegrationUnavailableException;
import org.gradle.internal.nativeplatform.NoOpTerminalDetector;
import org.gradle.internal.nativeplatform.TerminalDetector;
import org.gradle.internal.nativeplatform.jna.JnaBootPathConfigurer;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;

/**
 * @author: Szczepan Faber, created at: 9/12/11
 */
public class TerminalDetectorFactory {

    private static final Logger LOGGER = Logging.getLogger(TerminalDetectorFactory.class);

    public TerminalDetector create(JnaBootPathConfigurer jnaBootPathConfigurer) {
        try {
            jnaBootPathConfigurer.configure();
            return NativeServices.getInstance().get(TerminalDetector.class);
        } catch (NativeIntegrationUnavailableException e) {
            LOGGER.debug("Unable to initialise the native integration for current platform: " + OperatingSystem.current() + ". Details: " + e.getMessage());
            return new NoOpTerminalDetector();
        }
    }
}