/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.reflect;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Intercepts JavaFileManager calls to ignore files on the sourcepath.
 */
public class SourcepathIgnoringInvocationHandler implements InvocationHandler {
    private static final String HAS_LOCATION_METHOD = "hasLocation";
    private static final String LIST_METHOD = "list";
    private final Object proxied;

    public SourcepathIgnoringInvocationHandler(Object proxied) {
        this.proxied = proxied;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method m = proxied.getClass().getMethod(method.getName(), method.getParameterTypes());
        if (method.getName().equals(HAS_LOCATION_METHOD)) {
            // There is currently a requirement in the JDK9 javac implementation
            // that when javac is invoked with an explicitly empty sourcepath
            // (i.e. {@code --sourcepath ""}), it won't allow you to compile a java 9
            // module. However, we really want to explicitly set an empty sourcepath
            // so that we don't implicitly pull in unrequested sourcefiles which
            // haven't been snapshotted because we will consider the task up-to-date
            // if the implicit files change.
            //
            // This implementation of hasLocation() pretends that the JavaFileManager
            // has no concept of a source path.
            if (args[0].equals(StandardLocation.SOURCE_PATH)) {
                return false;
            }
        }
        if (method.getName().equals(LIST_METHOD)) {
            // If we are pretending that we don't have a sourcepath, the compiler will
            // look on the classpath for sources. Since we don't want to bring in any
            // sources implicitly from the classpath, we have to ignore source files
            // found on the classpath.
            if (args[0].equals(StandardLocation.CLASS_PATH)) {
                ((Set<JavaFileObject.Kind>) args[2]).remove(JavaFileObject.Kind.SOURCE);
            }
        }
        return m.invoke(proxied, args);
    }
}
