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
import org.codehaus.groovy.runtime.wrappers.Wrapper;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public class Instrumented {
    private static final Listener NO_OP = new Listener() {
        @Override
        public void systemPropertyQueried(String key, @Nullable Object value, String consumer) {
        }

        @Override
        public void envVariableQueried(String key, @Nullable String value, String consumer) {
        }
    };

    private static final AtomicReference<Listener> LISTENER = new AtomicReference<>(NO_OP);

    public static void setListener(Listener listener) {
        LISTENER.set(listener);
    }

    public static void discardListener() {
        setListener(NO_OP);
    }

    // Called by generated code
    @SuppressWarnings("unused")
    public static void groovyCallSites(CallSiteArray array) {
        for (CallSite callSite : array.array) {
            switch (callSite.getName()) {
                case "getProperty":
                    array.array[callSite.getIndex()] = new SystemPropertyCallSite(callSite);
                    break;
                case "properties":
                    array.array[callSite.getIndex()] = new SystemPropertiesCallSite(callSite);
                    break;
                case "getInteger":
                    array.array[callSite.getIndex()] = new IntegerSystemPropertyCallSite(callSite);
                    break;
                case "getLong":
                    array.array[callSite.getIndex()] = new LongSystemPropertyCallSite(callSite);
                    break;
                case "getBoolean":
                    array.array[callSite.getIndex()] = new BooleanSystemPropertyCallSite(callSite);
                    break;
                case "getenv":
                    array.array[callSite.getIndex()] = new GetEnvCallSite(callSite);
                    break;
            }
        }
    }

    // Called by generated code.
    public static String systemProperty(String key, String consumer) {
        return systemProperty(key, null, consumer);
    }

    // Called by generated code.
    public static String systemProperty(String key, @Nullable String defaultValue, String consumer) {
        String value = System.getProperty(key);
        systemPropertyQueried(key, value, consumer);
        return value == null ? defaultValue : value;
    }

    // Called by generated code.
    public static Properties systemProperties(String consumer) {
        return new AccessTrackingProperties(System.getProperties(), (k, v) -> {
            systemPropertyQueried(convertToString(k), convertToString(v), consumer);
        });
    }

    // Called by generated code.
    public static Integer getInteger(String key, String consumer) {
        systemPropertyQueried(key, consumer);
        return Integer.getInteger(key);
    }

    // Called by generated code.
    public static Integer getInteger(String key, int defaultValue, String consumer) {
        systemPropertyQueried(key, consumer);
        return Integer.getInteger(key, defaultValue);
    }

    // Called by generated code.
    public static Integer getInteger(String key, Integer defaultValue, String consumer) {
        systemPropertyQueried(key, consumer);
        return Integer.getInteger(key, defaultValue);
    }

    // Called by generated code.
    public static Long getLong(String key, String consumer) {
        systemPropertyQueried(key, consumer);
        return Long.getLong(key);
    }

    // Called by generated code.
    public static Long getLong(String key, long defaultValue, String consumer) {
        systemPropertyQueried(key, consumer);
        return Long.getLong(key, defaultValue);
    }

    // Called by generated code.
    public static Long getLong(String key, Long defaultValue, String consumer) {
        systemPropertyQueried(key, consumer);
        return Long.getLong(key, defaultValue);
    }

    // Called by generated code.
    public static boolean getBoolean(String key, String consumer) {
        systemPropertyQueried(key, consumer);
        return Boolean.getBoolean(key);
    }

    // Called by generated code.
    public static String getenv(String key, String consumer) {
        String value = System.getenv(key);
        envVariableQueried(key, value, consumer);
        return value;
    }

    // Called by generated code.
    public static Map<String, String> getenv(String consumer) {
        return new AccessTrackingEnvMap((key, value) -> envVariableQueried(convertToString(key), value, consumer));
    }

    private static void envVariableQueried(String key, String value, String consumer) {
        listener().envVariableQueried(key, value, consumer);
    }

    private static void systemPropertyQueried(String key, String consumer) {
        systemPropertyQueried(key, System.getProperty(key), consumer);
    }

    private static void systemPropertyQueried(String key, @Nullable String value, String consumer) {
        listener().systemPropertyQueried(key, value, consumer);
    }

    private static Listener listener() {
        return LISTENER.get();
    }

    private static Object unwrap(Object obj) {
        if (obj instanceof Wrapper) {
            return ((Wrapper) obj).unwrap();
        }
        return obj;
    }

    private static String convertToString(Object arg) {
        if (arg instanceof CharSequence) {
            return ((CharSequence) arg).toString();
        }
        return (String) arg;
    }

    public interface Listener {
        /**
         * @param consumer The name of the class that is reading the property value
         */
        void systemPropertyQueried(String key, @Nullable Object value, String consumer);

        /**
         * Invoked when the code reads the environment variable.
         *
         * @param key the name of the variable
         * @param value the value of the variable
         * @param consumer the name of the class that is reading the variable
         */
        void envVariableQueried(String key, @Nullable String value, String consumer);
    }

    private static class IntegerSystemPropertyCallSite extends AbstractCallSite {
        public IntegerSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Integer.class)) {
                return getInteger(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(Integer.class)) {
                return getInteger(arg1.toString(), (Integer) unwrap(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }
    }

    private static class LongSystemPropertyCallSite extends AbstractCallSite {
        public LongSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Long.class)) {
                return getLong(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(Long.class)) {
                return getLong(arg1.toString(), (Long) unwrap(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }
    }

    private static class BooleanSystemPropertyCallSite extends AbstractCallSite {
        public BooleanSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Boolean.class)) {
                return getBoolean(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }
    }

    private static class SystemPropertyCallSite extends AbstractCallSite {
        public SystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty(arg1.toString(), convertToString(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }
    }

    private static class SystemPropertiesCallSite extends AbstractCallSite {
        public SystemPropertiesCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object callGetProperty(Object receiver) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperties(array.owner.getName());
            } else {
                return super.callGetProperty(receiver);
            }
        }
    }

    private static class GetEnvCallSite extends AbstractCallSite {
        public GetEnvCallSite(CallSite prev) {
            super(prev);
        }

        @Override
        public Object call(Object receiver) throws Throwable {
            if (receiver.equals(System.class)) {
                return getenv(array.owner.getName());
            }
            return super.call(receiver);
        }

        @Override
        public Object call(Object receiver, Object arg1) throws Throwable {
            if (receiver.equals(System.class) && arg1 instanceof CharSequence) {
                return getenv(convertToString(arg1), array.owner.getName());
            }
            return super.call(receiver, arg1);
        }
    }
}
