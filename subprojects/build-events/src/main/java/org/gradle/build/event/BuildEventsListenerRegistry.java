/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.build.event;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;
import org.gradle.tooling.events.OperationCompletionListener;

/**
 * Allows a plugin to receive information about the operations that run as part of the current build.
 *
 * <p>An instance of this registry can be injected into tasks, plugins and other objects by annotating a public constructor or property getter method with {@code javax.inject.Inject}.</p>
 *
 * @since 6.1
 */
@Incubating
public interface BuildEventsListenerRegistry {
    /**
     * Subscribes the given listener to operation finish events, if not already subscribed. The listener receives events as each operation completes.
     */
    void subscribe(Provider<? extends OperationCompletionListener> listener);
}
