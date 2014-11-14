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

package org.gradle.play.internal;

import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.language.base.internal.AbstractLanguageSourceSet;

import javax.inject.Inject;

// TODO:DAZ This is a temporary (untyped) implementation of LanguageSourceSet
public class ScalaSources extends AbstractLanguageSourceSet {
    @Inject
    public ScalaSources(String name, String parent, FileResolver fileResolver) {
        super(name, parent, "Generated scala source", new DefaultSourceDirectorySet("gensrc", fileResolver));
    }
}
