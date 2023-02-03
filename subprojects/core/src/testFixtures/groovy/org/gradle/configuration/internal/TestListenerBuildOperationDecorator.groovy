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

package org.gradle.configuration.internal

import org.gradle.api.Action

class TestListenerBuildOperationDecorator implements ListenerBuildOperationDecorator {

    @Override
    <T> Action<T> decorate(String registrationPoint, Action<T> action) {
        action
    }

    @Override
    <T> Closure<T> decorate(String registrationPoint, Closure<T> closure) {
        return closure
    }

    @Override
    <T> T decorate(String registrationPoint, Class<T> cls, T listener) {
        listener
    }

    @Override
    Object decorateUnknownListener(String registrationPoint, Object listener) {
        listener
    }
}
