/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.MirahSourceSet;
import org.gradle.util.ConfigureUtil;

public class DefaultMirahSourceSet implements MirahSourceSet {
    private final SourceDirectorySet mirah;
    private final SourceDirectorySet allMirah;

    public DefaultMirahSourceSet(String displayName, FileResolver fileResolver) {
        mirah = new DefaultSourceDirectorySet(String.format("%s Mirah source", displayName), fileResolver);
        mirah.getFilter().include("**/*.mirah");
        allMirah = new DefaultSourceDirectorySet(String.format("%s Mirah source", displayName), fileResolver);
        allMirah.getFilter().include("**/*.mirah");
        allMirah.source(mirah);
    }

    public SourceDirectorySet getMirah() {
        org.gradle.api.logging.Logging.getLogger(DefaultMirahSourceSet.class).info("mirah="+mirah+".");
        return mirah;
    }

    public MirahSourceSet mirah(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getMirah());
        return this;
    }

    public SourceDirectorySet getAllMirah() {
        return allMirah;
    }
}