/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking;

import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.Collections;
import java.util.List;

public class InvalidLockFileException extends RuntimeException implements ResolutionProvider {
    private final String resolutionMessage;

    public InvalidLockFileException(String name, Exception cause, String resolutionMessage) {
        super("Invalid lock state for " + name, cause);
        this.resolutionMessage = resolutionMessage;
    }
    public InvalidLockFileException(String name, String resolutionMessage) {
        super("Invalid lock state for " + name);
        this.resolutionMessage = resolutionMessage;
    }

    @Override
    public List<String> getResolutions() {
        return Collections.singletonList(resolutionMessage);
    }
}
