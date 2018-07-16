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

package org.gradle.internal.configuration;

// Intended to support listener deregistration, probably in DefaultListenerManager
// Not implemented yet in this spike
// Needed as the listener registered in the ListenerManager is now a wrapper around the one passed in, so
// it won't match the same listener being passed to a deregistration call.
public interface ListenerDelegate {

    Object getDelegate();

}
