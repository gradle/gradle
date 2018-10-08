/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.vcs.internal.services;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.git.internal.DefaultGitVersionControlSpec;
import org.gradle.vcs.internal.DefaultVersionControlRepository;
import org.gradle.vcs.internal.VersionControlSpecFactory;

import java.net.URI;

public class DefaultVersionControlSpecFactory implements VersionControlSpecFactory {
    private final ObjectFactory objectFactory;
    private final NotationParser<String, ModuleIdentifier> notationParser;

    public DefaultVersionControlSpecFactory(ObjectFactory objectFactory, NotationParser<String, ModuleIdentifier> notationParser) {
        this.objectFactory = objectFactory;
        this.notationParser = notationParser;
    }

    @Override
    public <T extends VersionControlSpec> T create(Class<T> specType) {
        if (specType.isAssignableFrom(GitVersionControlSpec.class)) {
            return specType.cast(objectFactory.newInstance(DefaultGitVersionControlSpec.class));
        }
        throw new IllegalArgumentException(String.format("Do not know how to create an instance of %s.", specType.getName()));
    }

    @Override
    public <T extends VersionControlSpec> DefaultVersionControlRepository create(Class<T> specType, URI uri) {
        return objectFactory.newInstance(DefaultVersionControlRepository.class, uri, notationParser, create(specType));
    }
}
