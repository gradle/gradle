/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

import java.util.concurrent.atomic.AtomicReference;

public class Instrumented {
    private static final Listener NO_OP = (key, consumer) -> {
    };
    private static final AtomicReference<Listener> LISTENER = new AtomicReference<>(NO_OP);

    public static void setListener(Listener listener) {
        LISTENER.set(listener);
    }

    public static void discardListener() {
        LISTENER.set(NO_OP);
    }

    // Called by generated code
    public static void groovyCallSites(CallSiteArray array) {
        for (CallSite callSite : array.array) {
            if (callSite.getName().equals("getProperty")) {
                array.array[callSite.getIndex()] = new SystemPropertyCallSite(callSite);
            }
        }
    }

    // Called by generated code.
    public static String systemProperty(String key, String consumer) {
        LISTENER.get().systemPropertyQueried(key, consumer);
        return System.getProperty(key);
    }

    public interface Listener {
        void systemPropertyQueried(String key, String consumer);
    }

    private static class SystemPropertyCallSite extends AbstractCallSite {
        public SystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg1) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty((String) arg1, array.owner.getName());
            } else {
                return super.call(receiver, arg1);
            }
        }
    }
}
