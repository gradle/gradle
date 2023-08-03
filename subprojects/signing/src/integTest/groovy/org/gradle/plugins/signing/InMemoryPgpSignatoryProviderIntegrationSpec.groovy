/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.plugins.signing

import spock.lang.Issue

class InMemoryPgpSignatoryProviderIntegrationSpec extends SigningIntegrationSpec {

    def "signs with default signatory"() {
        given:
        buildFile << """
            signing {
                useInMemoryPgpKeys(project.property('secretKey'), project.property('password'))
                sign(jar)
            }
        """

        when:
        executer.withEnvironmentVars([
            ORG_GRADLE_PROJECT_secretKey: secretKeyWithPassword,
            ORG_GRADLE_PROJECT_password: password
        ])
        succeeds("signJar")

        then:
        executed(":signJar")
        file("build", "libs", "sign-1.0.jar.asc").exists()
    }

    def "signs with custom signatory"() {
        given:
        buildFile << """
            signing {
                useInMemoryPgpKeys('foo', 'bar')
                signatories {
                    custom(project.property('secretKey'), project.property('password'))
                }
                sign(jar)*.signatory = signatories.custom
            }
        """

        when:
        executer.withEnvironmentVars([
            ORG_GRADLE_PROJECT_secretKey: secretKeyWithPassword,
            ORG_GRADLE_PROJECT_password: password
        ])
        succeeds("signJar")

        then:
        executed(":signJar")
        file("build", "libs", "sign-1.0.jar.asc").exists()
    }

    def "supports keys without passwords"() {
        given:
        buildFile << """
            signing {
                useInMemoryPgpKeys(project.property('secretKey'), '')
                sign(jar)
            }
        """

        when:
        executer.withEnvironmentVars([
            ORG_GRADLE_PROJECT_secretKey: secretKeyWithoutPassword
        ])
        succeeds("signJar")

        then:
        executed(":signJar")
        file("build", "libs", "sign-1.0.jar.asc").exists()
    }

    @Issue("gradle/gradle#10363")
    def "supports signing subkeys"() {
        given:
        buildFile << """
            signing {
                useInMemoryPgpKeys(project.property('keyId'), project.property('secretKey'), project.property('password'))
                sign(jar)
            }
        """

        when:
        executer.withEnvironmentVars([
            ORG_GRADLE_PROJECT_keyId: keyId,
            ORG_GRADLE_PROJECT_secretKey: secretSubkeyWithPassword,
            ORG_GRADLE_PROJECT_password: subkeyPassword
        ])
        succeeds("signJar")

        then:
        executed(":signJar")
        file("build", "libs", "sign-1.0.jar.asc").exists()
    }

    final secretKeyWithPassword = '''\
-----BEGIN PGP PRIVATE KEY BLOCK-----

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
=AXAR
-----END PGP PRIVATE KEY BLOCK-----'''
    final password = 'foo'

    final secretKeyWithoutPassword = '''\
-----BEGIN PGP PRIVATE KEY BLOCK-----

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
=Y8Fu
-----END PGP PRIVATE KEY BLOCK-----'''

    final secretSubkeyWithPassword = '''\
-----BEGIN PGP PRIVATE KEY BLOCK-----

lQCVBF1jpksBBADcYrxfdWFLpzL6M1uilFT6De3jp7cxrD84Z1lCEdJnZ1SSLlxh
qJnFuptzw2SJLgbe11rkZi9B58i32KGfQeFo4esLC/I2JnfWEP6SFfWNxojrEGL7
hcVfMZcb7+Umxg7RTvP4eH+P4o4tDPsWzSThQWyfqupngvknDcjEstHnYwARAQAB
/gNlAkdOVQG0GUdyYWRsZSA8Z3JhZGxlQGxvY2FsaG9zdD6IuAQTAQIAIgUCXWOm
SwIbAwYLCQgHAwIGFQgCCQoLBBYCAwECHgECF4AACgkQXuXfa+0uJE5gPwP/cFcJ
3DcU6VVHa1FjQrCYNO9DglmG0AelqhcRNbnaX54nJcean9y2+Y5V6JyAsAwNPFUL
4/mFQjvYeHrzm5cWExcYwaRukJo3jLve2JKsBtecSnbNWObbrDVZhuOv0mN/zrDd
+uxxBB9/rr+yyTCKLMIzp3RRQvco+b6TTmZICyidAf0EXWOnNAEEAMQSptDgnV5R
Xh2XYg3jOfKVXIUm1+ocnTZXaPX8oQK8ztYPHYcxNnvxEL4yWqX9OifNF5bQdDNN
O2218a8/NQWx5anZXHkv6boXhxuydgQlEz+aUVf6O5+k2Xi+fVoOpfC03lYQi0Tm
wcLxFNP65FZ7pa/TTZH1WcJ7E9IV4kLBABEBAAH+AwMC0D/sjL6p9z9gLcdnJfu9
LwdhzgvcxeaTiQ45fMwJgtqlllPwdc6S9C7g5l3AO4A5SuqwSATz7CyriPLuJpyC
6RBea9AqC0Hic3/8Vrl4TH45jApOb7SYGI1EHQD28qbVafC+MPVAeLy7Y7EPeVN9
kkXKWB6xOm5kclknNKNVfR0U7Ps+ZXdK1+uEomRt6IN8EUWeW+o0VVS2J4YkY9wj
8/el2CZvWIU881fOBY5rjkISDeunSA2GlyTjv0rhNgYZ87GrqoD2Zk1mLQ+kXktg
HNLAtzXssDgO+ga3S1nAo1X9LhNzIE1WX0etAkeKkOHIetqWMHkXWU9KHPn7sppT
lT7kX2oYQ99mNHDKwvru1sCnGKWv3gAReEeymAf9V3GUDhyBJ2pVv8lwderUtZlh
LJtQOA6vuIvqrN2LoNCILT1CkAeDcnm6wXkJXhMt3HPYjzs0NdvvJs4IoT6/HXb3
ThU/Dz/AoiKtySv8fIkBPQQYAQIACQUCXWOnNAIbAgCoCRBe5d9r7S4kTp0gBBkB
AgAGBQJdY6c0AAoJEG5Ev8mCjFTQGMYEALuOyLu87cWW+vJUJptQIdwBn68ZmJnh
1ruFgn8SOs99mkOOfgO2pvmfAF3rJgDNUVW6Ly29mmwEWZUNYMcp6EZ+ItFglNmv
FT79DcECUSLdYZNxDy6lj3RMKdhMFx+74JB7A2DX3U0z1tXTqgtr82VDmVuBKXSs
pNdW+809T5mRJgIEAJ7xapChOfM0z5wXoNVTpDFRoL3Te0PMufMPH9111LXFSPNz
Uzw93D1R02l5c4l0yPKu6FmGhIg+NhTtxImdkTcN9t52Gc8ZdDfHgOk4qpysZriF
X7jZiv0UhMlU6Bkj0BAw0hDeB9tTf7FMXt1M/D700sBeMohxug/OF68hMtu2
=IMnM
-----END PGP PRIVATE KEY BLOCK-----'''
    final keyId = '828C54D0'
    final subkeyPassword = 'foo'

}
