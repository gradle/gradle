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
package org.gradle.internal.dispatch;

public class FailureHandlingDispatch<T> implements Dispatch<T> {
    private final Dispatch<? super T> dispatch;
    private final DispatchFailureHandler<? super T> handler;

    public FailureHandlingDispatch(Dispatch<? super T> dispatch, DispatchFailureHandler<? super T> handler) {
        this.dispatch = dispatch;
        this.handler = handler;
    }

    public void dispatch(T message) {
        try {
            dispatch.dispatch(message);
        } catch (Throwable throwable) {
            handler.dispatchFailed(message, throwable);
        }
    }
}
