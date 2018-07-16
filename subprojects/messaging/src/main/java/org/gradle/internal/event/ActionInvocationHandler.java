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

package org.gradle.internal.event;

import org.gradle.api.Action;
import org.gradle.internal.dispatch.FilteringDispatch;
import org.gradle.internal.dispatch.MethodInvocation;

// just busted out of BroadcastDispatch in this spike so that we can register them higher up
// and optionally decorate with ops
public class ActionInvocationHandler implements FilteringDispatch<MethodInvocation> {
    private final String methodName;
    private final Action action;

    public ActionInvocationHandler(String methodName, Action action) {
        this.methodName = methodName;
        this.action = action;
    }

    public void dispatch(MethodInvocation message) {
        if (willDispatch(message)) {
            action.execute(message.getArguments()[0]);
        }
    }

    @Override
    public boolean willDispatch(MethodInvocation message) {
        return message.getMethod().getName().equals(methodName);
    }
}
