/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.util.internal;

import org.gradle.api.NonNullApi;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Converts a wrapper distribution url to a URI.
 */
@NonNullApi
public class WrapperDistributionUrlConverter {
    /**
     * Converts the given distribution url to a URI.
     * <p>
     * If the url is relative, it is resolved against the given file root.
     * Otherwise, the URI is created from the url.
     *
     * @param distributionUrl The distribution url.
     * @param fileRoot The root directory to resolve relative urls against.
     * @return The URI.
     * @throws URISyntaxException If the url is not a valid URI.
     */
    public static URI convertDistributionUrl(String distributionUrl, File fileRoot) throws URISyntaxException {
        URI source = new URI(distributionUrl);
        if (source.getScheme() == null) {
            //  No scheme means someone passed a relative url.
            //  In our context only file relative urls make sense.
            return new File(fileRoot, source.getSchemeSpecificPart()).toURI();
        } else {
            return source;
        }
    }
}
