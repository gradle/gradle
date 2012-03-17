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

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * <p>Options to send to Ant's depend task. Depends will delete out of date class files before compiling.
 * This is not fool-proof, but will cut down on the frequency of having to do a clean build. This may or may
 * not be faster than a clean build.
 * See the <a href="http://ant.apache.org/manual/OptionalTasks/depend.html" target="_blank">Ant Reference</a>
 * for more information.
 *
 * <h2>Ant Options</h2>
 * <ul>
 *      <li>srcDir  - <b>IGNORED</b> - set automatically</li>
 *      <li>destDir - <b>IGNORED</b> - set automatically</li>
 *      <li>cache - <b>IGNORED</b> - set automatically</li>
 *      <li>closure - boolean controlling depth of dependency graph traversal</li>
 *      <li>dump - dump dependency information to log</li>
 *      <li>classpath - extra classes to check</li>
 *      <li>warnOnRmiStubs - disables warnings for rmi stubs with no source</li>
 * </ul>
 *
 * <p>
 * There is an additional <tt>useCache</tt> boolean option to enable/disable caching of dependency information. It is true
 * by default.
 *
 * @author Steve Appling
 */
public class DependOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private boolean useCache = true;

    public boolean isUseCache() {
        return useCache;
    }

    public void setUseCache(boolean useCache) {
        this.useCache = useCache;
    }

    private boolean closure;

    public boolean isClosure() {
        return closure;
    }

    public void setClosure(boolean closure) {
        this.closure = closure;
    }

    private boolean dump;

    public boolean isDump() {
        return dump;
    }

    public void setDump(boolean dump) {
        this.dump = dump;
    }

    private String classpath = "";

    public String getClasspath() {
        return classpath;
    }

    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    private boolean warnOnRmiStubs = true;

    public boolean isWarnOnRmiStubs() {
        return warnOnRmiStubs;
    }

    public void setWarnOnRmiStubs(boolean warnOnRmiStubs) {
        this.warnOnRmiStubs = warnOnRmiStubs;
    }

    public List<String> excludedFieldsFromOptionMap() {
        return ImmutableList.of("srcDir", "destDir", "cache", "useCache");
    }
}