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
package org.gradle.api.internal.project.antbuilder;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.classpath.ClassPath;

import java.lang.reflect.Method;
import java.util.Arrays;

public class MemoryLeakPrevention {

    private final static Logger LOG = Logging.getLogger(MemoryLeakPrevention.class);

    final static Method THREADLOCAL_REMOVE;

    static {
        Method m;
        try {
            m = ThreadLocal.class.getDeclaredMethod("remove");
        } catch (NoSuchMethodException e) {
            m = null;
        }
        THREADLOCAL_REMOVE = m;
    }

    private final String name; // only used to make debugging easier
    private final Strategy[] strategies;
    private final ClassLoader leakingLoader;
    private final ClassPath classPath;

    public MemoryLeakPrevention(String name, ClassLoader leakingLoader, ClassPath classPath) {
        this(name, leakingLoader, classPath,
            new GroovyJava7RuntimeMemoryLeakStrategy(),
            new JavaBeanIntrospectorMemoryLeakStrategy(),
            new ResourceBundleLeakStrategy());
    }

    public MemoryLeakPrevention(String name, ClassLoader leakingLoader, ClassPath classPath, Strategy... strategies) {
        this.name = name;
        this.classPath = classPath;
        this.strategies = strategies;
        this.leakingLoader = leakingLoader;
    }

    public ClassLoader getLeakingLoader() {
        return leakingLoader;
    }

    static abstract class Strategy {
        boolean appliesTo(ClassPath classpath) {
            return false;
        }

        // prepare is called before the classloader is given for consumption
        void prepare(ClassLoader leakingLoader, ClassLoader... affectedLoaders) throws Exception {
        }

        // pre-cleanup is executed right after the class loader has been used
        // it can be called multiple times, after each use, on the same thread
        // as the one which created the classloader
        void afterUse(ClassLoader leakingLoader, ClassLoader... affectedLoaders) throws Exception {

        }

        // cleanup is called before the classloader is disposed
        void dispose(ClassLoader classLoader, ClassLoader... affectedLoaders) throws Exception {
        }
    }

    private void doWithClassPath(StrategyAction action) {
        for (Strategy strategy : strategies) {
            if (strategy.appliesTo(classPath)) {
                try {
                    action.execute(strategy);
                } catch (Exception e) {
                    LOG.debug("Cannot apply memory leak strategy", e);
                }
            }
        }
    }

    public void dispose(final ClassLoader... affectedLoaders) {
        LOG.debug(String.format("Clearing leaking classloader [%s] from %s", name, Arrays.toString(affectedLoaders)));
        doWithClassPath(new StrategyAction() {
            @Override
            public void execute(Strategy strategy) throws Exception {
                strategy.afterUse(leakingLoader, affectedLoaders);
                strategy.dispose(leakingLoader, affectedLoaders);
            }
        });
    }

    public void prepare(final ClassLoader... affectedLoaders) {
        doWithClassPath(new StrategyAction() {
            @Override
            public void execute(Strategy strategy) throws Exception {
                strategy.prepare(leakingLoader, affectedLoaders);
            }
        });
    }

    public void afterUse(final ClassLoader... affectedLoaders) {
        doWithClassPath(new StrategyAction() {
            @Override
            public void execute(Strategy strategy) throws Exception {
                strategy.afterUse(leakingLoader, affectedLoaders);
            }
        });
    }

    private interface StrategyAction {
        void execute(Strategy s) throws Exception;
    }
}
