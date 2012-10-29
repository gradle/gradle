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

package org.gradle.api.publish.internal;

import org.gradle.api.Buildable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.publish.Publication;

/**
 * The internal contract for publication implemenations.
 *
 * It is assumed internally that all Publication implementations are also PublicationInternal implementations.
 *
 * @param <T> The contract type for the normalised version of this publication.
 */
public interface PublicationInternal<T extends NormalizedPublication> extends Publication, Buildable {

    /**
     * Returns all the local files that would be published in a publish operation.
     *
     * The files need not exist at the time this method is called.
     *
     * @return The local files that would be published in a publish operation.
     */
    FileCollection getPublishableFiles();

    /**
     * Returns a consume oriented description of this publication in the langauge of the publication format.
     *
     * It is an error to call this method before the publication is “ready”. That is, all of the files
     * and dependencies (i.e. tasks) that are part of this publication must be satisfied before this
     * method is called.
     *
     * @return The normalized publication
     * @see NormalizedPublication
     */
    T asNormalisedPublication();

    Class<T> getNormalisedPublicationType();

}
