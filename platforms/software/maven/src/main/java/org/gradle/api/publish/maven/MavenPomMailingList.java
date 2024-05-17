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

import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * A mailing list of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 * @see MavenPomMailingListSpec
 */
public interface MavenPomMailingList {

    /**
     * The name of this mailing list.
     */
    Property<String> getName();

    /**
     * The email address or link that can be used to subscribe to this mailing list.
     */
    Property<String> getSubscribe();

    /**
     * The email address or link that can be used to subscribe to this mailing list.
     */
    Property<String> getUnsubscribe();

    /**
     * The email address or link that can be used to post to this mailing list.
     */
    Property<String> getPost();

    /**
     * The URL where you can browse the archive of this mailing list.
     */
    Property<String> getArchive();

    /**
     * The alternate URLs where you can browse the archive of this mailing list.
     */
    SetProperty<String> getOtherArchives();

}
