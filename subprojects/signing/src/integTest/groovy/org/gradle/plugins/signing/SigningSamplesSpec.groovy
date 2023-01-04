/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.util.Requires
import org.junit.Rule

class SigningSamplesSpec extends AbstractSampleIntegrationTest {
    @Rule
    public final Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        using m2
    }

    @UsesSample('signing/conditional')
    def "conditional signing with dsl #dsl"() {
        given:
        inDirectory(sample.dir.file(dsl))

        when:
        run "publish"

        then:
        skipped(":signMainPublication")

        and:
        def module = repoFor(dsl).module('gradle', 'conditional', '1.0-SNAPSHOT')
        module.assertArtifactsPublished("maven-metadata.xml", "conditional-${module.publishArtifactVersion}.pom", "conditional-${module.publishArtifactVersion}.module", "conditional-${module.publishArtifactVersion}.jar")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample('signing/gnupg-signatory')
    @Requires(adhoc = { GpgCmdFixture.getAvailableGpg() != null })
    def "use gnupg signatory with dsl #dsl"() {
        setup:
        def projectDir = sample.dir.file(dsl)
        def symlink = GpgCmdFixture.setupGpgCmd(projectDir)

        when:
        inDirectory(projectDir)

        and:
        run "signArchives"

        then:
        projectDir.file("build/libs/gnupg-signatory-1.0.jar.asc").assertExists()

        cleanup:
        GpgCmdFixture.cleanupGpgCmd(symlink)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample('signing/maven-publish')
    def "publish attaches signatures with dsl #dsl"() {
        given:
        inDirectory(sample.dir.file(dsl))

        and:
        def artifactId = "my-library"
        def version = "1.0"
        def fileRepo = maven(sample.dir.file("$dsl/build/repos/releases"))
        def module = fileRepo.module("com.example", artifactId, version)

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        def expectedFileNames = ["${artifactId}-${version}.jar", "${artifactId}-${version}-sources.jar", "${artifactId}-${version}-javadoc.jar", "${artifactId}-${version}.pom", "${artifactId}-${version}.module"]
        module.assertArtifactsPublished(expectedFileNames.collect { [it, "${it}.asc"] }.flatten())

        and:
        module.parsedPom.name == "My Library"
        module.parsedPom.description == "A concise description of my library"
        module.parsedPom.url == "http://www.example.com/library"
        module.parsedPom.licenses[0].name.text() == "The Apache License, Version 2.0"
        module.parsedPom.licenses[0].url.text() == "http://www.apache.org/licenses/LICENSE-2.0.txt"
        module.parsedPom.developers[0].id.text() == "johnd"
        module.parsedPom.developers[0].name.text() == "John Doe"
        module.parsedPom.developers[0].email.text() == "john.doe@example.com"
        module.parsedPom.scm.connection.text() == 'scm:git:git://example.com/my-library.git'
        module.parsedPom.scm.developerConnection.text() == 'scm:git:ssh://example.com/my-library.git'
        module.parsedPom.scm.url.text() == 'http://example.com/my-library/'

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample('signing/in-memory')
    def "uses in-memory PGP keys with dsl #dsl"() {
        given:
        def projectDir = sample.dir.file(dsl)
        inDirectory(projectDir)

        when:
        executer.withEnvironmentVars([
            ORG_GRADLE_PROJECT_signingKey: secretKey,
            ORG_GRADLE_PROJECT_signingPassword: password
        ])
        succeeds("signStuffZip")

        then:
        projectDir.file('build/distributions/stuff.zip.asc').exists()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample('signing/in-memory-subkey')
    def "uses in-memory PGP subkeys with dsl #dsl"() {
        given:
        def projectDir = sample.dir.file(dsl)
        inDirectory(projectDir)

        when:
        executer.withEnvironmentVars([
            ORG_GRADLE_PROJECT_signingKeyId: secretSubkeyId,
            ORG_GRADLE_PROJECT_signingKey: secretSubkey,
            ORG_GRADLE_PROJECT_signingPassword: subkeyPassword
        ])
        succeeds("signStuffZip")

        then:
        projectDir.file('build/distributions/stuff.zip.asc').exists()

        where:
        dsl << ['groovy', 'kotlin']
    }

    MavenFileRepository repoFor(String dsl) {
        return maven(sample.dir.file("$dsl/build/repo"))
    }

    final secretKey = '''\
-----BEGIN PGP PRIVATE KEY BLOCK-----

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
=AXAR
-----END PGP PRIVATE KEY BLOCK-----'''
    final password = 'foo'

    final secretSubkey = '''\
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
    final secretSubkeyId = '828C54D0'
    final subkeyPassword = 'foo'
}
