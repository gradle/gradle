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
package org.gradle.tooling;

/**
 * A listener which is notified as some long running operation makes progress.
 * @since 1.0-milestone-3
 */
public interface ProgressListener {
    /**
     * Called when the progress status changes.
     *
     * @param event An event describing the status change.
     * @since 1.0-milestone-3
     */
    void statusChanged(ProgressEvent event);
}
