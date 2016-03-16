/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.gosu;

import org.gradle.language.gosu.tasks.BaseGosuCompileOptions;

/**
 * Options for Gosu compilation, including the use of the Ant-backed compiler.
 */
public class GosuCompileOptions extends BaseGosuCompileOptions {

    private boolean fork;
    private boolean useAnt = true;

    /**
     * Tells whether to use Ant for compilation. If {@code true}, the standard Ant gosuc task will be used for
     * Gosu compilation. If {@code false}, Gosu will be compiled in a Gradle Daemon Worker. Defaults to {@code true}.
     */
    public boolean isUseAnt() {
        return useAnt;
    }

    public void setUseAnt(boolean useAnt) {
        this.useAnt = useAnt;
        if (!useAnt) {
            setFork(true);
        }
    }

    /**
     * Whether to run the Gosu compiler in a separate process. Defaults to {@code false}
     * for the Ant based compiler ({@code useAnt = true}), and to {@code true} when
     * not using Ant ({@code useAnt = false}).
     */
    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Some compiler options are not recognizable by the gosuc ant task;
     * this method prevents incompatible values from being passed to the ant configuration closure
     * @param fieldName name of field to exclude from the dynamically generated ant script
     * @return true if the given fieldName should be excluded
     */
    @Override
    protected boolean excludeFromAntProperties(String fieldName) {
        return fieldName.equals("useAnt")
            || fieldName.equals("fork")
            || fieldName.equals("forkOptions")
            || fieldName.equals("listFiles");
    }

}
