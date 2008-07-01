/*
 * Copyright 2007-2008 the original author or authors.
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
 
package org.gradle.util;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public class ConfigureUtil {
    public static Closure extractClosure(Object[] args) {
        if (args.length > 0 && args[args.length - 1] instanceof Closure) {
            return (Closure) args[args.length - 1];
        }
        return null;
    }

    public static Object configure(Closure configureClosure, Object delegate) {
        return configure(configureClosure, delegate, Closure.DELEGATE_FIRST);
    }

    public static Object configure(Closure configureClosure, Object delegate, int resolveStrategy) {
        if (configureClosure == null) {
            return delegate;
        }
        configureClosure.setResolveStrategy(resolveStrategy);
        configureClosure.setDelegate(delegate);
        configureClosure.call();
        return delegate;
    }
}
