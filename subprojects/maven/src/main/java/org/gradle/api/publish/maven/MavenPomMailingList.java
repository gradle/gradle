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

import org.gradle.api.Incubating;

import java.util.List;

/**
 * A mailing list of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 * @see MavenPomMailingListSpec
 */
@Incubating
public interface MavenPomMailingList {

    /**
     * Returns the name of this mailing list.
     */
    String getName();

    /**
     * Sets the name of this mailing list.
     */
    void setName(String name);

    /**
     * Returns the email address or link that can be used to subscribe to this mailing list.
     */
    String getSubscribe();

    /**
     * Sets the email address or link that can be used to subscribe to this mailing list.
     */
    void setSubscribe(String subscribe);

    /**
     * Returns the email address or link that can be used to unsubscribe to this mailing list.
     */
    String getUnsubscribe();

    /**
     * Sets the email address or link that can be used to subscribe to this mailing list.
     */
    void setUnsubscribe(String unsubscribe);

    /**
     * Returns the email address or link that can be used to post to this mailing list.
     */
    String getPost();

    /**
     * Sets the email address or link that can be used to post to this mailing list.
     */
    void setPost(String post);

    /**
     * Sets the URL where you can browse the archive of this mailing list.
     */
    void setArchive(String archive);

    /**
     * Returns the URL where you can browse the archive of this mailing list.
     */
    String getArchive();

    /**
     * Adds alternate URLs where you can browse the archive of this mailing list.
     */
    void otherArchives(String... otherArchive);

    /**
     * Returns the alternate URLs where you can browse the archive of this mailing list.
     */
    List<String> getOtherArchives();

}
