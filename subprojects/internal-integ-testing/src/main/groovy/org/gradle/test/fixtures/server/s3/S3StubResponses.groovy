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

package org.gradle.test.fixtures.server.s3

import groovy.xml.StreamingMarkupBuilder
import org.gradle.test.fixtures.server.stub.StubResponse
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.tz.FixedDateTimeZone

import java.security.MessageDigest

class S3StubResponses {

    public static final DateTimeFormatter RCF_822_DATE_FORMAT = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss z")
            .withLocale(Locale.US)
            .withZone(new FixedDateTimeZone("GMT", "GMT", 0, 0));

   public static final String X_AMZ_REQUEST_ID = '0A398F9A1BAD4027'
   public static final String X_AMZ_ID_2 = 'nwUZ/n/F2/ZFRTZhtzjYe7mcXkxCaRjfrJSWirV50lN7HuvhF60JpphwoiX/sMnh'
   public static final String DATE_HEADER = 'Mon, 29 Sep 2014 11:04:27 GMT'
   public static final String SERVER_AMAZON_S3 = 'AmazonS3'

    StubResponse responseForPutFile(File file) {
        new StubResponse(
                status: 200,
                headers: [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        "ETag"            : calculateEtag(file),
                        'Server'          : SERVER_AMAZON_S3
                ]
        )
    }

    StubResponse responseForGetFile(File file) {
        return new StubResponse(
                status: 200,
                headers: [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'ETag'            : calculateEtag(file),
                        'Server'          : SERVER_AMAZON_S3,
                        'Accept-Ranges'   : 'bytes',
                        'Content-Type'    : 'application/octet-stream',
                        'Content-Length'  : "${file.length()}",
                        'Last-Modified'   : RCF_822_DATE_FORMAT.print(new Date().getTime())
                ],
                body: file.getBytes()
        )
    }

    StubResponse responseForHeadFile(File file) {
        return new StubResponse(
                status: 200,
                headers: [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'ETag'            : calculateEtag(file),
                        'Server'          : SERVER_AMAZON_S3,
                        'Accept-Ranges'   : 'bytes',
                        'Content-Type'    : 'application/octet-stream',
                        'Content-Length'  : "${file.length()}",
                        'Last-Modified'   : RCF_822_DATE_FORMAT.print(new Date().getTime())
                ]
        )
    }

    StubResponse responseForGetFileNotFound(String url) {
        def xml = new StreamingMarkupBuilder().bind {
            Error() {
                Code("NoSuchKey")
                Message("The specified key does not exist.")
                Key(url)
                RequestId("stubbedRequestId")
                HostId("stubbedHostId")
            }
        }
        new StubResponse(
                status: 404,
                headers: [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_AMAZON_S3,
                        'Content-Type'    : 'application/xml',
                ],
                body: xml.toString()
        )
    }

    StubResponse responseForHeadFileNotFound() {
        new StubResponse(
                status: 404,
                headers: [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_AMAZON_S3,
                        'Content-Type'    : 'application/xml',
                ]
        )
    }


    static calculateEtag(File file) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update(file.bytes);
        new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }
}
