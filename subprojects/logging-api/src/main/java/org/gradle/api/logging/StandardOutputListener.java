/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.logging;

/**
 * <p>A {@code StandardOutputListener} receives text written by Gradle's logging system to standard output or
 * error.</p>
 */
public interface StandardOutputListener {
    /**
     * Called when some output is written by the logging system.
     *
     * @param output The text.
     */
    void onOutput(CharSequence output);
}
