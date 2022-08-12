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

package org.gradle.integtests.resolve.support

trait RepositoryDslSupport {

    def url = "http://somerepo"
    def s3Url = "s3://somerepo"

    def simplestDsl() {
        """
        repositories {
            maven {
                url "${url}"
            }
        }
        """
    }

    def passwordCredentialsTyped() {
        """
        repositories {
            maven {
                url "${url}"
                credentials(PasswordCredentials) {
                    username "myUsername"
                    password "myPassword"
                }
            }
        }
        """
    }

    def awsCredentials() {
        """
        repositories {
            maven {
                url "${s3Url}"
                credentials(AwsCredentials) {
                    accessKey "myAccessKey"
                    secretKey "mySecret"
                }
            }
        }
        """

    }

    def ivyPasswordCredentials(){
        """
repositories {
    ivy {
        url "${url}"
        credentials {
            username 'targetUser'
            password 'targetPassword'
        }
    }
}
"""
    }
}
