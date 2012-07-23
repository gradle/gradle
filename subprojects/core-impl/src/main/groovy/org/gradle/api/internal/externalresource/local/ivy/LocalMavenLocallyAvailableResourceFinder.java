/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.externalresource.local.ivy;

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.mvnsettings.CannotLocateLocalMavenRepositoryException;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.externalresource.local.AbstractLocallyAvailableResourceFinder;
import org.gradle.internal.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class LocalMavenLocallyAvailableResourceFinder extends AbstractLocallyAvailableResourceFinder<ArtifactRevisionId> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalMavenLocallyAvailableResourceFinder.class);

    public LocalMavenLocallyAvailableResourceFinder(LocalMavenRepositoryLocator localMavenRepositoryLocator, String pattern) {
        super(createProducer(localMavenRepositoryLocator, pattern));
    }

    private static Transformer<Factory<List<File>>, ArtifactRevisionId> createProducer(final LocalMavenRepositoryLocator localMavenRepositoryLocator, final String pattern) {
        return new LazyLocalMavenPatternTransformer(localMavenRepositoryLocator, pattern);
    }

    private static class LazyLocalMavenPatternTransformer implements Transformer<Factory<List<File>>, ArtifactRevisionId> {
        private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
        private final String pattern;

        private PatternTransformer patternTransformer;
        private boolean initialized;

        public LazyLocalMavenPatternTransformer(LocalMavenRepositoryLocator localMavenRepositoryLocator, String pattern) {
            this.localMavenRepositoryLocator = localMavenRepositoryLocator;
            this.pattern = pattern;
        }

        public Factory<List<File>> transform(ArtifactRevisionId original) {
            if (!initialized) {
                initializedPatternTransformer();
            }

            if (patternTransformer != null) {
                return patternTransformer.transform(original);
            } else {
                return new Factory<List<File>>() {
                    public List<File> create() {
                        return Collections.emptyList();
                    }
                };
            }
        }

        private void initializedPatternTransformer() {
            try {
                File localMavenRepository = localMavenRepositoryLocator.getLocalMavenRepository();
                this.patternTransformer = new PatternTransformer(localMavenRepository, pattern);
            } catch (CannotLocateLocalMavenRepositoryException ex) {
                LOGGER.warn(String.format("Unable to locate local Maven repository."));
                LOGGER.debug(String.format("Problems while locating local maven repository.", ex));
            } finally {
                initialized = true;
            }
        }
    }
}
