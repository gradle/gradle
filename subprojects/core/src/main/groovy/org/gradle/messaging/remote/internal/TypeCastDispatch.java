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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.dispatch.Dispatch;

class TypeCastDispatch<S, T> implements Dispatch<T> {
    private final Class<S> type;
    private final Dispatch<? super S> dispatch;

    TypeCastDispatch(Class<S> type, Dispatch<? super S> dispatch) {
        this.type = type;
        this.dispatch = dispatch;
    }

    public void dispatch(T message) {
        dispatch.dispatch(type.cast(message));
    }
}
