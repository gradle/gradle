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

package org.gradle.plugins.javascript.jshint.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorker;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorkerUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.plugins.javascript.rhino.worker.RhinoWorkerUtils.*;

public class JsHintWorker implements RhinoWorker<JsHintResult, JsHintSpec> {

    private static final Logger LOGGER = Logging.getLogger(JsHintWorker.class);

    public JsHintResult process(JsHintSpec spec) {
        Scriptable jsHintScope = RhinoWorkerUtils.parse(spec.getJsHint(), "UTF-8");

        String encoding = spec.getEncoding();

        Map<File, Map<String, Object>> results = new LinkedHashMap<File, Map<String, Object>>();

        for (File target : spec.getSource()) {
            LOGGER.info("Reading file: {}", target.getAbsolutePath());
            String source = readFile(target, encoding);
            Map<String, Object> result = jsHint(jsHintScope, source, target.getName());
            LOGGER.warn("Result type: {}", result.getClass());
            results.put(target, result);
        }

        return new JsHintResult(results);
    }

    private Map<String, Object> jsHint(Scriptable jsHintScope, final String source, final String sourceName) {
        return childScope(jsHintScope, new DefaultScopeOperation<Map<String, Object>>() {
            public Map<String, Object> action(Scriptable scope, Context context) {
                scope.put("jsHintSource", scope, source);
                Object data = context.evaluateString(scope, "JSHINT(jsHintSource); JSHINT.data();", sourceName, 0, null);
                return toMap((Scriptable) data);
            }
        });
    }

    public Exception convertException(RhinoException rhinoException) {
        // TODO - need to convert this to a non rhino type in case the version is different back at the client
        return rhinoException;
    }

    private Map<String, Object> toMap(Scriptable obj) {
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

            map.put(key, toValue(value));
        }

        return map;
    }

    private Object toValue(Object object) {
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
