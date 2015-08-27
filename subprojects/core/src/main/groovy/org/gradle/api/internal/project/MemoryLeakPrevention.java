/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.project;

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

import java.util.ArrayList;

public class MemoryLeakPrevention {
    private static Strategy[] STRATEGIES = { new GroovyRuntimeMemoryLeakStrategy(), new JavaBeanIntrospectorMemoryLeakStrategy() };

    interface Strategy {
        boolean appliesTo(ClassPath classpath);
        void cleanup(ClassLoader classLoader) throws Exception;
    }

    public void preventMemoryLeaks(ClassPath classpath, ClassLoader classLoader) {
        ArrayList<Throwable> disposeErrors = new ArrayList<Throwable>();
        for (Strategy strategy : STRATEGIES) {
            if (strategy.appliesTo(classpath)) {
                try {
                    strategy.cleanup(classLoader);
                } catch (Exception e) {
                    disposeErrors.add(e);
                }
            }
        }
        if (!disposeErrors.isEmpty()) {
            if (disposeErrors.size()==1) {
                throw new RuntimeException(disposeErrors.get(0));
            } else {
                throw new DefaultMultiCauseException("Unable to dispose runtime", disposeErrors);
            }
        }
    }

}
