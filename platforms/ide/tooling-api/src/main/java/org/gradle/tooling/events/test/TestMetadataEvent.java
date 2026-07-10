/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.tooling.events.test;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.ProgressEvent;
import org.jspecify.annotations.NullMarked;

/**
 * An event emitted by tests that contain additional data about the test.
 * <p>
 * To access data from the event:
 * <pre>
 *         TestLauncher launcher = ...;
 *         launcher.addProgressListener(new ProgressListener() {
 *             void statusChanged(ProgressEvent event) {
 *                 if (event instanceof TestMetadataEvent) {
 *                     if (event instanceof TestFileAttachmentMetadataEvent) {
 *                         // Do something with file attachment
 *                     } else if (event instanceof TestKeyValueMetadataEvent) {
 *                         // Do something with key-values
 *                     } else {
 *                         // ignore unrecognized events
 *                     }
 *                 }
 *             }
 *         });
 * </pre>
 *
 * @see TestKeyValueMetadataEvent
 * @see TestFileAttachmentMetadataEvent
 * @since 8.13
 */
@Incubating
@NullMarked
public interface TestMetadataEvent extends ProgressEvent {

}
