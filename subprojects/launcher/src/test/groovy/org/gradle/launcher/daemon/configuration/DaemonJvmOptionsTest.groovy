/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.configuration

import org.gradle.api.internal.file.TestFiles
import org.gradle.process.internal.JvmOptions
import spock.lang.Specification

class DaemonJvmOptionsTest extends Specification {

    def "#propDescr is immutable system property"() {
        when:
        def opts = createOpts()
        opts.jvmArgs(propAsArg)

        then:
        opts.allImmutableJvmArgs.contains(propAsArg.toString())
        and:
        opts.immutableSystemProperties.containsKey(propKey)

        where:
        propDescr                 | propKey                                   | propAsArg
        "ssl keystore path"       | DaemonJvmOptions.SSL_KEYSTORE_KEY         | "-D${DaemonJvmOptions.SSL_KEYSTORE_KEY}=/keystore/path"
        "ssl keystore password"   | DaemonJvmOptions.SSL_KEYSTOREPASSWORD_KEY | "-D${DaemonJvmOptions.SSL_KEYSTOREPASSWORD_KEY}=secret"
        "ssl keystore type"       | DaemonJvmOptions.SSL_KEYSTORETYPE_KEY     | "-D${DaemonJvmOptions.SSL_KEYSTORETYPE_KEY}=jks"
        "ssl truststore path"     | DaemonJvmOptions.SSL_TRUSTSTORE_KEY       | "-D${DaemonJvmOptions.SSL_TRUSTSTORE_KEY}=truststore/path"
        "ssl truststore password" | DaemonJvmOptions.SSL_TRUSTPASSWORD_KEY    | "-D${DaemonJvmOptions.SSL_TRUSTPASSWORD_KEY}=secret"
        "ssl truststore type"     | DaemonJvmOptions.SSL_TRUSTSTORETYPE_KEY   | "-D${DaemonJvmOptions.SSL_TRUSTSTORETYPE_KEY}=jks"
    }

    def "#propDescr can be set as systemproperty"() {
        JvmOptions opts = createOpts()
        when:
        opts.systemProperty(propKey, propValue)
        then:
        opts.allJvmArgs.contains("-D${propKey}=${propValue}".toString());
        where:
        propDescr                 | propKey                                   | propValue
        "ssl keystore path"       | DaemonJvmOptions.SSL_KEYSTORE_KEY         | "/keystore/path"
        "ssl keystore password"   | DaemonJvmOptions.SSL_KEYSTOREPASSWORD_KEY | "secret"
        "ssl keystore type"       | DaemonJvmOptions.SSL_KEYSTORETYPE_KEY     | "jks"
        "ssl truststore path"     | DaemonJvmOptions.SSL_TRUSTSTORE_KEY       | "truststore/path"
        "ssl truststore password" | DaemonJvmOptions.SSL_TRUSTPASSWORD_KEY    | "secret"
        "ssl truststore type"     | DaemonJvmOptions.SSL_TRUSTSTORETYPE_KEY   | "jks"
    }

    def "all single use immutable jvm args has 4 elements" () {
        when:
        def opts = createOpts()

        then:
        opts.allSingleUseImmutableJvmArgs.size() == 4;
    }

    private DaemonJvmOptions createOpts() {
        return new DaemonJvmOptions(TestFiles.fileCollectionFactory())
    }
}
