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
package org.gradle.api.internal.classloading;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.classpath.ClassPath;

import java.util.Arrays;
import java.util.Set;

public class MemoryLeakPrevention {
    private final static Logger LOG = Logging.getLogger(MemoryLeakPrevention.class);
    private final String name; // only used to make debugging easier
    private final Set<Strategy> strategies;
    private final ClassLoader leakingLoader;

    public MemoryLeakPrevention(String name, ClassLoader leakingLoader, ClassPath classPath) {
        this(name, leakingLoader, classPath, new GroovyJava7RuntimeMemoryLeakStrategy());
    }

    public MemoryLeakPrevention(String name, ClassLoader leakingLoader, final ClassPath classPath, Strategy... strategies) {
        this.name = name;
        this.strategies = Sets.newHashSet(Iterables.filter(Arrays.asList(strategies), new Predicate<Strategy>() {
            @Override
            public boolean apply(Strategy input) {
                return input.appliesTo(classPath);
            }
        }));
        this.leakingLoader = leakingLoader;
    }

    public ClassLoader getLeakingLoader() {
        return leakingLoader;
    }

    public static abstract class Strategy {
        public boolean appliesTo(ClassPath classpath) {
            return false;
        }

        // prepare is called before the classloader is given for consumption
        public void prepare(ClassLoader leakingLoader, ClassLoader... affectedLoaders) throws Exception {
        }

        // cleanup is called before the classloader is disposed
        public void dispose(ClassLoader classLoader, ClassLoader... affectedLoaders) throws Exception {
        }
    }

    private void doWithClassPath(Action<? super Strategy> action) {
        for (Strategy strategy : strategies) {
            action.execute(strategy);
        }
    }

    public void dispose(final ClassLoader... affectedLoaders) {
        LOG.debug(String.format("Clearing leaking classloader [%s] from %s", name, Arrays.toString(affectedLoaders)));
        doWithClassPath(new ErroringAction<Strategy>() {
            @Override
            protected void doExecute(Strategy strategy) throws Exception {
                strategy.dispose(leakingLoader, affectedLoaders);
            }
        });
    }

    public void prepare(final ClassLoader... affectedLoaders) {
        doWithClassPath(new ErroringAction<Strategy>() {
            @Override
            protected void doExecute(Strategy strategy) throws Exception {
                strategy.prepare(leakingLoader, affectedLoaders);
            }
        });
    }
}
