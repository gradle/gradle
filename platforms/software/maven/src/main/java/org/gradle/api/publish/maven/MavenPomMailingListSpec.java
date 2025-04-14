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

package org.gradle.api.publish.maven;

import org.gradle.api.Action;

/**
 * Allows to add mailing lists to a Maven publication.
 *
 * @see MavenPom
 * @see MavenPomMailingList
 * @since 4.8
 */
public interface MavenPomMailingListSpec {

    /**
     * Creates, configures and adds a mailing list to the publication.
     */
    void mailingList(Action<? super MavenPomMailingList> action);

}
