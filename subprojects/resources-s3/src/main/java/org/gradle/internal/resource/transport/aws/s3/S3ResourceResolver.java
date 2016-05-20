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

import java.util.ArrayList;
import java.util.List;

public class S3ResourceResolver {

    private S3ResourceNameExtractor nameExtractor = new S3ResourceNameExtractor();

    public List<String> resolveResourceNames(ObjectListing objectListing) {
        List<String> results = new ArrayList<String>();

        results.addAll(resolveFileResourceNames(objectListing));
        results.addAll(resolveDirectoryResourceNames(objectListing));

        return results;
    }

    private List<String> resolveFileResourceNames(ObjectListing objectListing) {
        List<String> results = new ArrayList<String>();
        List<S3ObjectSummary> objectSummaries = objectListing.getObjectSummaries();
        if (null != objectSummaries) {
            for (S3ObjectSummary objectSummary : objectSummaries) {
                String key = objectSummary.getKey();
                String fileName = nameExtractor.extractResourceName(key);
                if (null != fileName) {
                    results.add(fileName);
                }
            }
        }

        return results;
    }

    private List<String> resolveDirectoryResourceNames(ObjectListing objectListing) {
        List<String> results = new ArrayList<String>();
        List<String> commonPrefixes = objectListing.getCommonPrefixes();
        if(null != commonPrefixes) {
            for(String prefix : commonPrefixes) {
                String dirName = nameExtractor.extractDirectoryName(prefix);
                if(null != dirName) {
                    results.add(dirName);
                }
            }
        }

        return results;
    }
}
