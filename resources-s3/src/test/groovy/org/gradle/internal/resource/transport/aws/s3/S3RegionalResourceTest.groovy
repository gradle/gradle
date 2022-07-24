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

package org.gradle.internal.resource.transport.aws.s3

import com.amazonaws.regions.RegionUtils
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.Region
import com.google.common.base.Optional
import spock.lang.Specification

import static com.amazonaws.regions.Region.getRegion

class S3RegionalResourceTest extends Specification {

    def "should determine the aws region from virtual hosted urls"() {
        expect:
        S3RegionalResource regionalResource = new S3RegionalResource(uri)
        regionalResource.region == expectedRegion
        regionalResource.bucketName == expectedBucket
        regionalResource.key == expectedKey


        where:
        uri                                                                           | expectedRegion                                                      | expectedBucket  | expectedKey

        new URI("s3://somebucket.au/a/b/file.txt")                                    | Optional.absent()                                                   | 'somebucket.au' | 'a/b/file.txt'
        new URI("s3://somebucket.au.s3.amazonaws.com/a/b/file.txt")                   | Optional.absent()                                                   | 'somebucket.au' | 'a/b/file.txt'
        new URI("s3://somebucket.au.s3-external-1.amazonaws.com/a/b/file.txt")        | Optional.of(getRegion(Regions.US_EAST_1))                           | 'somebucket.au' | 'a/b/file.txt'
        new URI("s3://somebucket.au.s3.eu-central-1.amazonaws.com/a/b/file.txt")      | Optional.of(getRegion(Regions.EU_CENTRAL_1))                        | 'somebucket.au' | 'a/b/file.txt'
        new URI("s3://somebucket.au.s3-eu-central-1.amazonaws.com/a/b/file.txt")      | Optional.of(getRegion(Regions.EU_CENTRAL_1))                        | 'somebucket.au' | 'a/b/file.txt'
        new URI("s3://somebucket.au.s3-ap-southeast-2.amazonaws.com/a/b/file.txt")    | Optional.of(getRegion(Regions.AP_SOUTHEAST_2))                      | 'somebucket.au' | 'a/b/file.txt'
        new URI("s3://somebucket.au.s3.cn-north-1.amazonaws.com.cn/a/b/file.txt")     | Optional.of(RegionUtils.getRegion(Region.CN_Beijing.firstRegionId)) | 'somebucket.au' | 'a/b/file.txt'

        new URI("s3://somebucket-1.2.3/a/b/file.txt")                                 | Optional.absent()                                                   | 'somebucket-1.2.3' | 'a/b/file.txt'
        new URI("s3://somebucket-1.2.3.s3.amazonaws.com/a/b/file.txt")                | Optional.absent()                                                   | 'somebucket-1.2.3' | 'a/b/file.txt'
        new URI("s3://somebucket-1.2.3.s3-external-1.amazonaws.com/a/b/file.txt")     | Optional.of(getRegion(Regions.US_EAST_1))                           | 'somebucket-1.2.3' | 'a/b/file.txt'
        new URI("s3://somebucket-1.2.3.s3.eu-central-1.amazonaws.com/a/b/file.txt")   | Optional.of(getRegion(Regions.EU_CENTRAL_1))                        | 'somebucket-1.2.3' | 'a/b/file.txt'
        new URI("s3://somebucket-1.2.3.s3-eu-central-1.amazonaws.com/a/b/file.txt")   | Optional.of(getRegion(Regions.EU_CENTRAL_1))                        | 'somebucket-1.2.3' | 'a/b/file.txt'
        new URI("s3://somebucket-1.2.3.s3-ap-southeast-2.amazonaws.com/a/b/file.txt") | Optional.of(getRegion(Regions.AP_SOUTHEAST_2))                      | 'somebucket-1.2.3' | 'a/b/file.txt'
        new URI("s3://somebucket-1.2.3.s3.cn-north-1.amazonaws.com.cn/a/b/file.txt")  | Optional.of(RegionUtils.getRegion(Region.CN_Beijing.firstRegionId)) | 'somebucket-1.2.3' | 'a/b/file.txt'
    }
}
