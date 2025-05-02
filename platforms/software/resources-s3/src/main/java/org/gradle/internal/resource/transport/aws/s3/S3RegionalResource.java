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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.google.common.base.Optional;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3RegionalResource {
    //https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-bucket-intro.html#virtual-host-style-url-ex
    private static final Pattern REGIONAL_ENDPOINT_PATTERN = Pattern.compile("^s3:\\/\\/(.+)?\\.s3[.-]([a-z0-9-]+)\\.amazonaws\\.com(\\.[a-z]+)?\\/(.+)");

    //https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-bucket-intro.html#accessing-a-bucket-using-S3-format
    private static final Pattern FALLBACK_ENDPOINT_PATTERN = Pattern.compile("^[a-z0-9]+:\\/\\/([^\\/]+)\\/(.+)");

    private final URI uri;
    private Optional<Region> region;
    private String bucketName;
    private String key;

    public S3RegionalResource(URI uri) {
        this.uri = uri;
        configure();
    }

    public Optional<Region> getRegion() {
        return region;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getKey() {
        return key;
    }


    private void configure() {
        Matcher matcher = REGIONAL_ENDPOINT_PATTERN.matcher(uri.toString());
        if (matcher.find()) {
            String bucketName = matcher.group(1);
            String region = matcher.group(2);
            String key = matcher.group(4);
            Region derivedRegion;
            if (region.equals("external-1")) {
                derivedRegion = Region.getRegion(Regions.US_EAST_1);
            } else {
                derivedRegion = RegionUtils.getRegion(region);
            }

            this.region = Optional.of(derivedRegion);
            this.bucketName = bucketName;
            this.key = key;

            return;
        }

        matcher = FALLBACK_ENDPOINT_PATTERN.matcher(uri.toString());
        if (matcher.find()) {
            this.region = Optional.absent();
            this.bucketName = getBucketName(matcher.group(1));
            this.key = matcher.group(2);

            return;
        }

        throw new RuntimeException(String.format("Can't parse S3 URI '%s'", uri));
    }

    private String getBucketName(String bucket) {
        return bucket.replaceAll("\\.s3\\.amazonaws\\.com", "").replaceAll("\\.s3-external-1\\.amazonaws\\.com", "");
    }
}
