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

package org.gradle.internal.resource.transport.aws.s3;

import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3ResourceResolver {

    private static final Pattern FILENAME_PATTERN = Pattern.compile("[^/]+\\.*$");

    private static final Function<S3ObjectSummary, String> EXTRACT_FILE_NAME = new Function<S3ObjectSummary, String>() {
        @Override
        public String apply(S3ObjectSummary input) {
            Matcher matcher = FILENAME_PATTERN.matcher(input.getKey());
            if (matcher.find()) {
                String group = matcher.group(0);
                return group.contains(".") ? group : null;
            }
            return null;
        }
    };

    public List<String> resolveResourceNames(ObjectListing objectListing) {
        List<String> results = new ArrayList<String>();

        results.addAll(resolveFileResourceNames(objectListing));
        results.addAll(resolveDirectoryResourceNames(objectListing));

        return results;
    }

    private List<String> resolveFileResourceNames(ObjectListing objectListing) {
        List<S3ObjectSummary> objectSummaries = objectListing.getObjectSummaries();
        if (null != objectSummaries) {
            return ImmutableList.copyOf(Iterables.filter(
                Iterables.transform(objectSummaries, EXTRACT_FILE_NAME),
                Predicates.notNull()
            ));
        }
        return Collections.emptyList();

    }

    private List<String> resolveDirectoryResourceNames(ObjectListing objectListing) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        if (objectListing.getCommonPrefixes() != null) {
            for (String prefix : objectListing.getCommonPrefixes()) {
                /**
                 * The common prefixes will also include the prefix of the <code>ObjectListing</code>
                 */
                String directChild = prefix.split(Pattern.quote(objectListing.getPrefix()))[1];
                if (directChild.endsWith("/")) {
                    builder.add(directChild.substring(0, directChild.length() - 1));
                } else {
                    builder.add(directChild);
                }
            }
            return builder.build();
        }
        return Collections.emptyList();
    }
}
