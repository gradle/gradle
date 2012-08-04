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

package org.gradle.plugins.javascript.envjs.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorker;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

import static org.gradle.plugins.javascript.rhino.worker.RhinoWorkerUtils.DefaultScopeOperation;
import static org.gradle.plugins.javascript.rhino.worker.RhinoWorkerUtils.parseRhino;

public class EnvJsEvaluateWorker implements RhinoWorker<String, EnvJsEvaluateSpec> {

    private static final Logger LOGGER = Logging.getLogger(EnvJsEvaluateWorker.class);

    public String process(EnvJsEvaluateSpec spec) {

        final String targetUrl = spec.getUrl();

        return parseRhino(spec.getEnvJs(), new DefaultScopeOperation<String>() {
            @Override
            public void initContext(Context context) {
                context.setOptimizationLevel(-1);
            }

            @Override
            public String action(Scriptable scope, Context context) {
                scope.put("targetUrl", scope, targetUrl);
                context.evaluateString(scope, "Envjs({scriptTypes: {'': true, 'text/javascript': true}});", targetUrl, 0, null);
                Object html = context.evaluateString(scope, "window.location = targetUrl; document.getElementsByTagName('html')[0].innerHTML;", targetUrl, 0, null);
                return (String) html;
            }
        });
    }

    public Exception convertException(RhinoException rhinoException) {
        // TODO - need to convert this to a non rhino type in case the version is different back at the client
        return rhinoException;
    }

}
