/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.javascript.engine;

import org.gradle.api.GradleException;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 */
public class TriremeJavascriptEngine implements JavascriptEngine, Serializable {
    @SuppressWarnings("rawtypes")
    public ScriptResult execute(ClassLoader cl, String scriptName, String script, String[] args) {
        ScriptResult result;
        try {
            Class nodeEnvironmentClass = cl.loadClass("io.apigee.trireme.core.NodeEnvironment");
            @SuppressWarnings("unchecked") Constructor nodeEnvironmentConstructor = nodeEnvironmentClass.getConstructor();

            Object nodeEnvironment = nodeEnvironmentConstructor.newInstance();
            Object nodeScript = callMethod(nodeEnvironment, "createScript", new Object[] {scriptName, script, args});
            Object scriptFuture = callMethod(nodeScript, "execute", null);
            Object scriptStatus = callMethod(scriptFuture, "get", null);
            Integer exitCode = (Integer) callMethod(scriptStatus, "getExitCode", null);

            Boolean isOk = (Boolean) callMethod(scriptStatus, "isOk", null);
            if (isOk) {
                result = new ScriptResult(exitCode);
            } else {
                Throwable cause = (Throwable) callMethod(scriptStatus, "getCause", null);
                result = new ScriptResult(cause);
            }
        } catch (Exception e) {
            throw new GradleException("Error invoking Trireme javascript engine.", e);
        }
        return result;
    }

    public String getErrorMessage(int status) {
        switch(status) {
            case 0:
                return "Success";
            case -1:
                return "An exception occurred while executing script";
            case -2:
                return "The script was canceled before it could complete";
            case -3:
                return "The script timed out before it could complete";
            default:
                return String.format("Unknown exit code: %d", status);
        }
    }

    @SuppressWarnings("rawtypes")
    private static Object callMethod(Object object, String methodName, Object[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class[] classes;
        if (args != null) {
            classes = new Class[args.length];
            for (int i=0; i<args.length; i++) {
                classes[i] = args[i].getClass();
            }
        } else {
            classes = null;
        }

        Method method = object.getClass().getMethod(methodName, classes);
        return method.invoke(object, args);
    }
}
