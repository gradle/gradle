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

package org.gradle.internal.actor;

import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.internal.dispatch.DispatchException;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.dispatch.ProxyDispatchAdapter;
import org.gradle.internal.dispatch.ReflectionDispatch;

public class TestActorFactory implements ActorFactory {
    @Override
    public Actor createActor(Object target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Actor createBlockingActor(final Object target) {
        return new Actor() {
            @Override
            public <T> T getProxy(Class<T> type) {
                return new ProxyDispatchAdapter<T>(new ReflectionDispatch(target), type, ThreadSafe.class).getSource();
            }

            @Override
            public void stop() throws DispatchException {
            }

            @Override
            public void dispatch(MethodInvocation message) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
