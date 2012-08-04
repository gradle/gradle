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

package org.gradle.messaging.dispatch;

import org.gradle.internal.UncheckedException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionDispatch implements Dispatch<MethodInvocation> {
    private final Object target;

    public ReflectionDispatch(Object target) {
        this.target = target;
    }

    public void dispatch(MethodInvocation message) {
        try {
            Method method = message.getMethod();
            method.setAccessible(true);
            method.invoke(target, message.getArguments());
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Throwable throwable) {
            throw UncheckedException.throwAsUncheckedException(throwable);
        }
    }
}
