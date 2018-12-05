/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.cpp.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.language.cpp.tasks.internal.DefaultCppCompileSpec;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.nativeplatform.CppLanguageStandard;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

import javax.inject.Inject;

/**
 * Compiles C++ source files into object files.
 */
@Incubating
@CacheableTask
public class CppCompile extends AbstractNativeSourceCompileTask {
    private final Property<CppLanguageStandard> langStandard;

    @Inject
    public CppCompile(ObjectFactory objectFactory) {
        langStandard = objectFactory.property(CppLanguageStandard.class);
    }

    public Property<CppLanguageStandard> getLanguageStandard() {
        return langStandard;
    }

    @Override
    protected NativeCompileSpec createCompileSpec() {
        return new DefaultCppCompileSpec();
    }
}
