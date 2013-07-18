/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.language.cpp.internal;

import groovy.lang.Closure;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.nativecode.language.base.internal.AbstractBaseSourceSet;
import org.gradle.nativecode.language.cpp.CppSourceSet;
import org.gradle.util.ConfigureUtil;

public class DefaultCppSourceSet extends AbstractBaseSourceSet implements CppSourceSet {
    public DefaultCppSourceSet(String name, String functionalSourceSetName, ProjectInternal project) {
        super(name, functionalSourceSetName, project, "C++");
    }

    public CppSourceSet exportedHeaders(Closure closure) {
        ConfigureUtil.configure(closure, getExportedHeaders());
        return this;
    }


    public CppSourceSet source(Closure closure) {
        ConfigureUtil.configure(closure, getSource());
        return this;
    }


}
