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

package org.gradle.internal.logging.console;

public interface Console {
    TextArea getBuildOutputArea();

    BuildProgressArea getBuildProgressArea();

    // TODO(ew): Consider whether this belongs in BuildProgressArea or here
    StyledLabel getStatusBar();

    /**
     * Flushes any pending updates. Updates may or may not be buffered, and this method should be called to finish rendering and pending updates, such as
     * updating the status bar.
     */
    void flush();
}
