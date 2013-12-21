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

package org.gradle.messaging.remote.internal;

import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.Receive;

/**
 * <p>A messaging endpoint which allows push-style dispatch and pull-style receive.
 *
 * <p>Implementations are not guaranteed to be completely thread-safe.
 * However, the implementations:
 * <ul>
 * <li>should allow separate threads for dispatching and receiving, i.e. single thread that dispatches
 * and a different single thread that receives should be perfectly safe</li>
 * <li>should allow stopping or requesting stopping from a different thread than receiving/dispatching</li>
 * <li>don't guarantee allowing multiple threads dispatching</li>
 * </li>
 * </ul>
 */
public interface Connection<T> extends Dispatch<T>, Receive<T>, AsyncStoppable {
}
