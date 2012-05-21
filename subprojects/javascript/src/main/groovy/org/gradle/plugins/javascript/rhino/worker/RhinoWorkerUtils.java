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

import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.io.*;

public abstract class RhinoWorkerUtils {

    public static String readFile(File file, String encoding) {
        try {
            return FileUtils.readFileToString(file, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeFile(String content, File destination, String encoding) {
        try {
            FileUtils.writeStringToFile(destination, content, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Scriptable parse(File source, String encoding) {
        return parse(source, encoding, null);
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

    public static interface ScopeOperation<T> {
        void initContext(Context context);
        T action(Scriptable scope, Context context);
    }

    public static class DefaultScopeOperation<T> implements ScopeOperation<T> {
        public void initContext(Context context) {}
        public T action(Scriptable scope, Context context) { return null; }
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

}
