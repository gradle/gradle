/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.foundation.output.definitions;

import org.gradle.foundation.output.FileLink;

import java.util.List;

/**
 * .
 */
public interface FileLinkDefinition {
    /**
     * @return a name that really only useful for debugging
     */
    String getName();

    /**
     * @return the regular expression used to find a potential FileLink
     */
    String getSearchExpression();

    /**
     * This is called for each match. Parse this to turn it into a FileLink.
     *
     * <!    Name        Description>
     *
     * @param fullSearchText the full text that was searched
     * @param matchedText the text that was matched
     * @param start the index into the entire searched text where the matchedText starts
     * @param end the index into the entire searched text where the matchedText ends
     * @return a FileLink or null if this is a false positive
     */
    int parseFileLink(String fullSearchText, String matchedText, int start, int end, boolean verifyFileExists, List<FileLink> fileLinks);
}
