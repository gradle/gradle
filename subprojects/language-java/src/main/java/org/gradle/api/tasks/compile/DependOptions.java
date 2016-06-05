/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.compile;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

/**
 * Options for the Ant Depend task. Only take effect if {@code CompileOptions.useAnt} and
 * {@code CompileOptions.useDepend} are {@code true}.
 *
 * <p>The Ant Depend task will delete out-of-date and dependent class files before compiling
 * so that only those files will be recompiled. This is not fool-proof but may result in faster compilation.
 * See the <a href="http://ant.apache.org/manual/Tasks/depend.html" target="_blank">Ant Reference</a>
 * for more information.
 *
 * <p>The {@code srcDir}, {@code destDir}, and {@code cache} properties of the Ant task
 * are set automatically. The latter is replaced by a {@code useCache} option to enable/disable caching of
 * dependency information.
 */
public class DependOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private static final ImmutableSet<String> EXCLUDE_FROM_ANT_PROPERTIES = ImmutableSet.of("srcDir", "destDir", "cache", "useCache");

    private boolean useCache = true;

    private boolean closure;

    private boolean dump;

    private String classpath = "";

    private boolean warnOnRmiStubs = true;

    /**
     * Tells whether to cache dependency information. Defaults to {@code true}.
     */
    @Input
    public boolean isUseCache() {
        return useCache;
    }

    /**
     * Sets whether to cache dependency information. Defaults to {@code true}.
     */
    public void setUseCache(boolean useCache) {
        this.useCache = useCache;
    }

    /**
     * Tells whether to delete the transitive closure of outdated files or only their
     * direct dependencies. Defaults to {@code false}.
     */
    @Input
    public boolean isClosure() {
        return closure;
    }

    /**
     * Sets whether to delete the transitive closure of outdated files or only their
     * direct dependencies. Defaults to {@code false}.
     */
    public void setClosure(boolean closure) {
        this.closure = closure;
    }

    /**
     * Tells whether to log dependency information. Defaults to {@code false}.
     */
    @Console
    public boolean isDump() {
        return dump;
    }

    /**
     * Sets whether to log dependency information. Defaults to {@code false}.
     */
    public void setDump(boolean dump) {
        this.dump = dump;
    }

    /**
     * Returns the compile classpath for which dependencies should also be checked.
     * Defaults to the empty string.
     */
    @Optional @Input
    public String getClasspath() {
        return classpath;
    }

    /**
     * Sets the compile classpath for which dependencies should also be checked.
     * Defaults to the empty string.
     */
    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    /**
     * Tells whether to warn on RMI stubs without source. Defaults to {@code true}.
     */
    @Internal
    public boolean isWarnOnRmiStubs() {
        return warnOnRmiStubs;
    }

    /**
     * Sets whether to warn on RMI stubs without source. Defaults to {@code true}.
     */
    public void setWarnOnRmiStubs(boolean warnOnRmiStubs) {
        this.warnOnRmiStubs = warnOnRmiStubs;
    }

    @Override
    protected boolean excludeFromAntProperties(String fieldName) {
        return EXCLUDE_FROM_ANT_PROPERTIES.contains(fieldName);
    }
}
