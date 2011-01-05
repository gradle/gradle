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
package org.gradle.api.internal.artifacts.dsl;

/**
 * An implementation of ClasspathScriptTransformer for use in build scripts.  This subclass defines the script method
 * name to be buildscript {}.
 */
public class BuildScriptClasspathScriptTransformer extends ClasspathScriptTransformer {
    private final String classpathClosureName;

    public BuildScriptClasspathScriptTransformer(String classpathClosureName) {
        this.classpathClosureName = classpathClosureName;
    }

    public String getId() {
        return classpathClosureName;
    }

    protected String getScriptMethodName() {
        return classpathClosureName;
    }
}


