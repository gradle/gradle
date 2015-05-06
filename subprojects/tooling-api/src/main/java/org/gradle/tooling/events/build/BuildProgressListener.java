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

package org.gradle.tooling.events.build;

import org.gradle.api.Incubating;

/**
 * A listener which is notified as part of running a build make progress.
 *
 * @see org.gradle.tooling.LongRunningOperation#addBuildProgressListener(BuildProgressListener)
 * @since 2.5
 */
@Incubating
public interface BuildProgressListener {
    /**
     * Called when the build execution progresses.
     *
     * The following events are currently issued:
     * <ul>
     *     <li>{@link BuildStartEvent}</li>
     *     <li>{@link BuildFinishEvent}</li>
     * </ul>
     *
     * You can find out more about the build operation for which progress is reported by querying the build descriptor using {@link BuildProgressEvent#getDescriptor()}.
     *
     * @param event An event describing the build operation progress.
     * @see BuildProgressEvent#getDescriptor()
     */
    void statusChanged(BuildProgressEvent event);

}
