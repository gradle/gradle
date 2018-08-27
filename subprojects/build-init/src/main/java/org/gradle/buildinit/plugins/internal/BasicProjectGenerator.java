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

package org.gradle.buildinit.plugins.internal;

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;

import java.util.Collections;
import java.util.Set;

public class BasicProjectGenerator implements ProjectGenerator {
    private final BuildScriptBuilderFactory scriptBuilderFactory;

    public BasicProjectGenerator(BuildScriptBuilderFactory scriptBuilderFactory) {
        this.scriptBuilderFactory = scriptBuilderFactory;
    }

    @Override
    public String getId() {
        return "basic";
    }

    @Override
    public void generate(InitSettings settings) {
        scriptBuilderFactory.script(settings.getDsl(), "build")
            .fileComment("This is a general purpose Gradle build.\n"
                + "Learn how to create Gradle builds at https://guides.gradle.org/creating-new-gradle-builds/")
            .create()
            .generate();
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        return BuildInitDsl.GROOVY;
    }

    @Override
    public boolean supportsPackage() {
        return false;
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return BuildInitTestFramework.NONE;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Collections.singleton(BuildInitTestFramework.NONE);
    }
}
