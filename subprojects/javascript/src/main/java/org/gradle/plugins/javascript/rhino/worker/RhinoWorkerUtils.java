/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.rhino.worker;

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.GFileUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This should have originally been an internal API.
 */
@Deprecated
public abstract class RhinoWorkerUtils {

    public interface ScopeOperation<T> {
        void initContext(Context context);
        T action(Scriptable scope, Context context);
    }

    public static class DefaultScopeOperation<T> implements ScopeOperation<T> {
        public void initContext(Context context) {}
        public T action(Scriptable scope, Context context) {
            return null;
        }
    }

    public static String readFile(File file, String encoding) {
        return GFileUtils.readFile(file, encoding);
    }

    public static void writeFile(String content, File destination, String encoding) {
        GFileUtils.writeFile(content, destination, encoding);
    }

    public static Scriptable parse(File source, String encoding) {
        return parse(source, encoding, null);
    }

    public static <T> T parseRhino(File rhinoScript, ScopeOperation<T> operation) {
        Context context = Context.enter();
        try {
            operation.initContext(context);
            Scriptable scope = context.initStandardObjects();
            String printFunction = "function print(message) {}";
            context.evaluateString(scope, printFunction, "print", 1, null);
            context.evaluateString(scope, readFile(rhinoScript, "UTF-8"), rhinoScript.getName(), 1, null);
            return operation.action(scope, context);
        } finally {
            Context.exit();
        }
    }

    public static Scriptable parse(File source, String encoding, Action<Context> contextConfig) {
        Context context = Context.enter();
        if (contextConfig != null) {
            contextConfig.execute(context);
        }

        Scriptable scope = context.initStandardObjects();
        try {
            Reader reader = new InputStreamReader(new FileInputStream(source), encoding);
            try {
                context.evaluateReader(scope, reader, source.getName(), 0, null);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            Context.exit();
        }

        return scope;
    }

    public static <R> R childScope(Scriptable parentScope, ScopeOperation<R> operation) {
        Context context = Context.enter();
        try {
            operation.initContext(context);
            Scriptable childScope = context.newObject(parentScope);
            childScope.setParentScope(parentScope);
            return operation.action(childScope, context);
        } finally {
            Context.exit();
        }
    }

    public static Map<String, Object> toMap(Scriptable obj) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();

        for (Object id : obj.getIds()) {
            String key;
            Object value;
            if (id instanceof String) {
                key = (String) id;
                value = obj.get(key, obj);
            } else if (id instanceof Integer) {
                key = id.toString();
                value = obj.get((Integer) id, obj);
            } else {
                throw new IllegalArgumentException(String.format("Unexpected key type: %s (value: %s)", id.getClass().getName(), id));
            }

            map.put(key, toJavaValue(value));
        }

        return map;
    }

    public static Object toJavaValue(Object object) {
        if (object == null || object.equals(Context.getUndefinedValue())) {
            return null;
        } else if (object.getClass().getPackage().getName().startsWith("java.")) {
            return object;
        } else if (object instanceof FunctionObject) {
            throw new IllegalArgumentException(String.format("Cannot convert function object to value (object: %s)", object));
        } else if (object instanceof Scriptable) {
            return toMap((Scriptable) object);
        } else {
            throw new IllegalArgumentException(String.format("Can't convert JS object %s (type: %s) to native Java object", object, object.getClass().getName()));
        }
    }


}
