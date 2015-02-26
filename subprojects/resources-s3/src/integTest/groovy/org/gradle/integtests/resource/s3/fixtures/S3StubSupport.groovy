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

package org.gradle.integtests.resource.s3.fixtures

import groovy.xml.StreamingMarkupBuilder
import org.gradle.test.fixtures.server.stub.HttpStub
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.tz.FixedDateTimeZone

import java.security.MessageDigest

class S3StubSupport {
    private static final DateTimeZone GMT = new FixedDateTimeZone("GMT", "GMT", 0, 0)
    protected static final DateTimeFormatter RCF_822_DATE_FORMAT = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss z")
            .withLocale(Locale.US)
            .withZone(GMT);

    public static final String ETAG = 'd41d8cd98f00b204e9800998ecf8427e'
    public static final String X_AMZ_REQUEST_ID = '0A398F9A1BAD4027'
    public static final String X_AMZ_ID_2 = 'nwUZ/n/F2/ZFRTZhtzjYe7mcXkxCaRjfrJSWirV50lN7HuvhF60JpphwoiX/sMnh'
    public static final String DATE_HEADER = 'Mon, 29 Sep 2014 11:04:27 GMT'
    public static final String SERVER_AMAZON_S3 = 'AmazonS3'
    final URI endpoint
    final S3StubServer server

    S3StubSupport(S3StubServer server) {
        this.server = server
        this.endpoint = new URI(server.address)
    }

    def stubPutFile(File file, String url) {
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'PUT'
                path = url
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
                body = file.getBytes()

            }
            response {
                status = 200
                headers = [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        "ETag"            : calculateEtag(file),
                        'Server'          : SERVER_AMAZON_S3
                ]
            }
        }
        server.expect(httpStub)
    }

    def stubMetaData(File file, String url) {
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                        'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'ETag'            : calculateEtag(file),
                        'Server'          : SERVER_AMAZON_S3,
                        'Accept-Ranges'   : 'bytes',
                        'Content-Type'    : 'application/octet-stream',
                        'Content-Length'  : "0",
                        'Last-Modified'   : RCF_822_DATE_FORMAT.print(new Date().getTime())
                ]
            }
        }
        server.expect(httpStub)
    }

    def stubMetaDataBroken(String url) {
        stubMetaDataLightWeightGet(url, 500)
    }

    def stubMetaDataMissing(String url) {
        stubFileNotFound(url)
    }

    private stubMetaDataLightWeightGet(String url, int statusCode) {
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                        'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = statusCode
                headers = [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_AMAZON_S3,
                        'Content-Type'    : 'application/xml',
                ]
            }
        }
        server.expect(httpStub)
    }

    def stubGetFile(File file, String url) {
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                        'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
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
                body = file.getBytes()
            }
        }
        server.expect(httpStub)
    }

    def stubListFile(File file, String bucketName, prefix = 'maven/release/', delimiter = '/') {
        def xml = new StreamingMarkupBuilder().bind {
            ListBucketResult(xmlns: "http://s3.amazonaws.com/doc/2006-03-01/") {
                Name(bucketName)
                Prefix(prefix)
                Marker()
                MaxKeys('1000')
                Delimiter(delimiter)
                IsTruncated('false')
                Contents {
                    Key(prefix)
                    LastModified('2014-09-21T06:44:09.000Z')
                    ETag(ETAG)
                    Size('0')
                    Owner {
                        ID("${(1..57).collect { 'a' }.join()}")
                        DisplayName('me')
                    }
                    StorageClass('STANDARD')
                }
                file.listFiles().each { currentFile ->
                    Contents {
                        Key(prefix + currentFile.name)
                        LastModified('2014-10-01T13:03:29.000Z')
                        ETag(ETAG)
                        Size(currentFile.length())
                        Owner {
                            ID("${(1..57).collect { 'a' }.join()}")
                            DisplayName('me')
                        }
                        StorageClass('STANDARD')
                    }
                    CommonPrefixes {
                        Prefix("${prefix}com/")
                    }
                }
                Contents {
                    Key(prefix + file.name)
                    LastModified('2014-10-01T13:03:29.000Z')
                    ETag(ETAG)
                    Size('19')
                    Owner {
                        ID("${(1..57).collect { 'a' }.join()}")
                        DisplayName('me')
                    }
                    StorageClass('STANDARD')
                }
                CommonPrefixes {
                    Prefix("${prefix}com/")
                }
            }
        }

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = "/${bucketName}/"
                headers = [
                        'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                        'Connection'  : 'Keep-Alive'
                ]
                params = [
                        'prefix'   : [prefix],
                        'delimiter': [delimiter],
                        'max-keys' : ["1000"]
                ]
            }
            response {
                status = 200
                headers = [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_AMAZON_S3,
                        'Content-Type'    : 'application/xml',
                ]
                body = xml.toString()
            }
        }
        server.expect(httpStub)
    }

    def stubGetFileAuthFailure(String url) {
        def xml = new StreamingMarkupBuilder().bind {
            Error() {
                Code("InvalidAccessKeyId")
                Message("The AWS Access Key Id you provided does not exist in our records.")
                AWSAccessKeyId("notRelevant")
                RequestId("stubbedAuthFailureRequestId")
                HostId("stubbedAuthFailureHostId")
            }
        }

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 403
                headers = [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_AMAZON_S3,
                        'Content-Type'    : 'application/xml',
                ]
                body = xml.toString()
            }
        }
        server.expect(httpStub)
    }


    def stubPutFileAuthFailure(String url) {
        def xml = new StreamingMarkupBuilder().bind {
            Error() {
                Code("InvalidAccessKeyId")
                Message("The AWS Access Key Id you provided does not exist in our records.")
                AWSAccessKeyId("notRelevant")
                RequestId("stubbedAuthFailureRequestId")
                HostId("stubbedAuthFailureHostId")
            }
        }

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'PUT'
                path = url
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 403
                headers = [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_AMAZON_S3,
                        'Content-Type'    : 'application/xml',
                ]
                body = xml.toString()
            }
        }
        server.expect(httpStub)
    }

    def stubFileNotFound(String url) {
        def xml = new StreamingMarkupBuilder().bind {
            Error() {
                Code("NoSuchKey")
                Message("The specified key does not exist.")
                Key(url)
                RequestId("stubbedRequestId")
                HostId("stubbedHostId")
            }
        }

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 404
                headers = [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_AMAZON_S3,
                        'Content-Type'    : 'application/xml',
                ]
                body = xml.toString()
            }
        }
        server.expect(httpStub)
    }

    def stubGetFileBroken(String url) {
        HttpStub httpStub = HttpStub.stubInteraction {
            def xml = new StreamingMarkupBuilder().bind {
                Error() {
                    Code("Internal Server Error")
                    Message("Something went seriously wrong")
                    Key(url)
                    RequestId("stubbedRequestId")
                    HostId("stubbedHostId")
                }
            }
            request {
                method = 'GET'
                path = url
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 500
                headers = [
                        'x-amz-id-2'      : X_AMZ_ID_2,
                        'x-amz-request-id': X_AMZ_REQUEST_ID,
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_AMAZON_S3,
                        'Content-Type'    : 'application/xml',
                ]
                body = xml.toString()
            }

        }
        server.expect(httpStub)
    }

    def calculateEtag(File file) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update(file.bytes);
        new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }
}
