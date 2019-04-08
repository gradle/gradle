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

package org.gradle

import org.gradle.test.fixtures.file.TestFile

class DistributionIntegritySpec extends DistributionIntegrationSpec {

    /*
     * Integration test to verify the integrity of the dependencies. The goal is to be able to check the dependencies
     * even we assume that the Gradle binaries are compromised. Ultimately this test should run outside of the Gradle.
     */

    @Override
    String getDistributionLabel() {
        'bin'
    }

    def "verify 3rd-party dependencies jar hashes"() {
        setup:
        // dependencies produced by Gradle and cannot be verified by this test
        def excluded = ['fastutil-8.2.1-min.jar', 'kotlin-compiler-embeddable-1.3.21-patched-for-gradle-5.4.jar']

        def expectedHashes = [
            'annotations-13.0.jar' : '919f0dfe192fb4e063e7dacadee7f8bb9a2672a9',
            'ant-1.9.13.jar' : '7aff87f91ffda6916751e39bb5688f0a53710ec4',
            'ant-launcher-1.9.13.jar' : '24cf1a899bb4b69373dc4cd000bb52a9f46c459d',
            'asm-7.0.jar' : 'd74d4ba0dee443f68fb2dcb7fcdb945a2cd89912',
            'asm-analysis-7.0.jar' : '4b310d20d6f1c6b7197a75f1b5d69f169bc8ac1f',
            'asm-commons-7.0.jar' : '478006d07b7c561ae3a92ddc1829bca81ae0cdd1',
            'asm-tree-7.0.jar' : '29bc62dcb85573af6e62e5b2d735ef65966c4180',
            'commons-compress-1.18.jar' : '1191f9f2bc0c47a8cce69193feb1ff0a8bcb37d5',
            'commons-io-2.6.jar' : '815893df5f31da2ece4040fe0a12fd44b577afaf',
            'commons-lang-2.6.jar' : '0ce1edb914c94ebc388f086c6827e8bdeec71ac2',
            'groovy-all-1.0-2.5.4.jar' : '11524aca7cdd69a736c22ca231a0eb7d7e4af7c6',
            'guava-26.0-android.jar' : 'ef69663836b339db335fde0df06fb3cd84e3742b',
            'jansi-1.17.1.jar' : 'e90caa31c9b8d748359450d7487f76b05549ae65',
            'javax.inject-1.jar' : '6975da39a7040257bd51d21a231b76c915872d38',
            'jcl-over-slf4j-1.7.25.jar' : 'f8c32b13ff142a513eeb5b6330b1588dcb2c0461',
            'jsr305-3.0.2.jar' : '25ea2e8b0c338a877313bd4672d3fe056ea78f0d',
            'jul-to-slf4j-1.7.25.jar' : '0af5364cd6679bfffb114f0dec8a157aaa283b76',
            'kotlin-reflect-1.3.21.jar' : 'd0d5ff2ac2ebd8a42697af41e20fc225a23c5d3b',
            'kotlin-sam-with-receiver-compiler-plugin-1.3.21.jar' : '8775cb948d1b3141b01522297f583c4938473fe8',
            'kotlin-script-runtime-1.3.21.jar' : '29363d474ee6fda354900636320a177c7286def9',
            'kotlin-stdlib-1.3.21.jar' : '4bcc2012b84840e19e1e28074284cac908be0295',
            'kotlin-stdlib-common-1.3.21.jar' : 'f30e4a9897913e53d778f564110bafa1fef46643',
            'kotlin-stdlib-jdk7-1.3.21.jar' : 'd207ce2c9bcf17dc8e51bab4dbfdac4d013e7138',
            'kotlin-stdlib-jdk8-1.3.21.jar' : 'd0634d54452abc421db494ad32dd215e6591c49f',
            'kotlinx-metadata-jvm-0.0.5.jar' : '314b06bf2b29d8205ac9a199da76fa627de1b0f5',
            'kryo-2.24.0.jar' : '0c6b206e80cfd97e66a1364003724491c757b92f',
            'log4j-over-slf4j-1.7.25.jar' : 'a87bb47468f47ee7aabbd54f93e133d4215769c3',
            'minlog-1.2.jar' : '59bfcd171d82f9981a5e242b9e840191f650e209',
            'native-platform-0.17.jar' : 'a36dcb015c813c7815315a4a0077d8c3d9cde117',
            'native-platform-freebsd-amd64-libcpp-0.17.jar' : '8ab2957d0b00ab9c93dc373e034f95a83bcf5496',
            'native-platform-freebsd-amd64-libstdcpp-0.17.jar' : '22610dd12007b9a58138e9da816744b492638322',
            'native-platform-freebsd-i386-libcpp-0.17.jar' : 'e0e555c98023ba2dba0c734ae1b2ab6eaffc9520',
            'native-platform-freebsd-i386-libstdcpp-0.17.jar' : '8cd139e8028941eec88afa846f3fdef777c2b4f3',
            'native-platform-linux-amd64-0.17.jar' : 'e7b11190423da33c2adf99f0af2108ff873a2857',
            'native-platform-linux-amd64-ncurses5-0.17.jar' : 'e045cb667f8bcd9eda334bd1894466635bf07d13',
            'native-platform-linux-amd64-ncurses6-0.17.jar' : '58ada06523b81864e64b5918ebed6178e2696ba5',
            'native-platform-linux-i386-0.17.jar' : 'b78e9c8ff6bb75df774511410fa2acdbb5272afa',
            'native-platform-linux-i386-ncurses5-0.17.jar' : '9bed7c6b8d341d15aaedf904851df1c9cb7f42d6',
            'native-platform-linux-i386-ncurses6-0.17.jar' : '4549a1b767711f5189e9e4bd88bed947b04f0868',
            'native-platform-osx-amd64-0.17.jar' : '0d5aff95ab809a893b4d4c17d37157cbd31e7c0c',
            'native-platform-windows-amd64-0.17.jar' : '0876c031ed6dba9f57e9aa75b58aa1331866babe',
            'native-platform-windows-amd64-min-0.17.jar' : 'e146903b7f5d82679b7e43783feacc2693cd29de',
            'native-platform-windows-i386-0.17.jar' : '4baf0469916f55af1d0a6ccb5cec6227dd0607bc',
            'native-platform-windows-i386-min-0.17.jar' : 'cd49c6a025d6cd452d97b57e92abdfa5fc938c81',
            'objenesis-2.6.jar' : '639033469776fd37c08358c6b92a4761feb2af4b',
            'plugins/aether-api-1.13.1.jar' : 'e48292eae5e14ec44978aa53debb1af7ddd6df93',
            'plugins/aether-connector-wagon-1.13.1.jar' : '4919bcf865d83a4529d92498d2079aee20bb2698',
            'plugins/aether-impl-1.13.1.jar' : 'ba2656934fa7c0f20c0c3882873dc705e16ae201',
            'plugins/aether-spi-1.13.1.jar' : 'c62b02d2a5a3939fded72039dd83e5b8ed42d45e',
            'plugins/aether-util-1.13.1.jar' : 'c8487ceb499b9ced96694731810acd1a70e13aca',
            'plugins/apiguardian-api-1.0.0.jar' : '3ef5276905e36f4d8055fe3cb0bdcc7503ffc85d',
            'plugins/asm-util-7.0.jar' : '18d4d07010c24405129a6dbb0e92057f8779fb9d',
            'plugins/aws-java-sdk-core-1.11.407.jar' : 'f4b641995bdfed7574d42973f18fc53f0b31748c',
            'plugins/aws-java-sdk-kms-1.11.407.jar' : '8781bc25d4957f915aaf8b041e2abf4a925dd5d8',
            'plugins/aws-java-sdk-s3-1.11.407.jar' : '9655501cf884d687fdecff6a0cf03955c6d7b59a',
            'plugins/bcpg-jdk15on-1.61.jar' : '422656435514ab8a28752b117d5d2646660a0ace',
            'plugins/bcprov-jdk15on-1.61.jar' : '00df4b474e71be02c1349c3292d98886f888d1f7',
            'plugins/biz.aQute.bndlib-4.0.0.jar' : '21e1d6fd1874d9bc201f2de1d0a48e84bff4149d',
            'plugins/bsh-2.0b6.jar' : 'fb418f9b33a0b951e9a2978b4b6ee93b2707e72f',
            'plugins/commons-codec-1.11.jar' : '3acb4705652e16236558f0f4f2192cc33c3bd189',
            'plugins/dd-plist-1.21.jar' : '4b8e4a6f35d39cd70b1c39d9c253233c4f0c7171',
            'plugins/google-api-client-1.25.0.jar' : 'e7ff725e89ff5dcbed107be1a24e8102ae2441ee',
            'plugins/google-api-services-storage-v1-rev136-1.25.0.jar' : '4fc59f4750f6fb96a77cdcd32a87c8f5fdbe7236',
            'plugins/google-http-client-1.25.0.jar' : '5fb16523c393bfe0210c29db44742bd308311841',
            'plugins/google-http-client-jackson2-1.25.0.jar' : '7c5c89bd4d0d34d9f1cb3396e8da6233e5074b5c',
            'plugins/google-oauth-client-1.25.0.jar' : 'c9ee14e8b095b4b301b28d57755cc482b8d6f39f',
            'plugins/gson-2.8.5.jar' : 'f645ed69d595b24d4cf8b3fbb64cc505bede8829',
            'plugins/hamcrest-core-1.3.jar' : '42a25dc3219429f0e5d060061f71acb49bf010a0',
            'plugins/httpclient-4.5.6.jar' : '1afe5621985efe90a92d0fbc9be86271efbe796f',
            'plugins/httpcore-4.4.10.jar' : 'acc54d9b28bdffe4bbde89ed2e4a1e86b5285e2b',
            'plugins/ion-java-1.0.2.jar' : 'ee9dacea7726e495f8352b81c12c23834ffbc564',
            'plugins/ivy-2.3.0.jar' : 'c5ebf1c253ad4959a29f4acfe696ee48cdd9f473',
            'plugins/jackson-annotations-2.9.8.jar' : 'ba7f0e6f8f1b28d251eeff2a5604bed34c53ff35',
            'plugins/jackson-core-2.9.8.jar' : '0f5a654e4675769c716e5b387830d19b501ca191',
            'plugins/jackson-databind-2.9.8.jar' : '11283f21cc480aa86c4df7a0a3243ec508372ed2',
            'plugins/jatl-0.2.3.jar' : '4074050ca38adc98920929362534e8d56b51ff7e',
            'plugins/jaxb-impl-2.3.1.jar' : 'a1a12b85ba1435b4189e065f7dafcc3fb9410d38',
            'plugins/jcifs-1.3.17.jar' : '1a2f0e28cc9497fb4927ea2422605fb8023969b6',
            'plugins/jcommander-1.72.jar' : '6375e521c1e11d6563d4f25a07ce124ccf8cd171',
            'plugins/jmespath-java-1.11.407.jar' : '9fb174ee5bb47dfd5cd34432e95630a06846f974',
            'plugins/joda-time-2.10.jar' : 'f66c8125d1057ffce6c4e29e624cac863e110e2b',
            'plugins/jsch-0.1.54.jar' : 'da3584329a263616e277e15462b387addd1b208d',
            'plugins/junit-4.12.jar' : '2973d150c0dc1fefe998f834810d68f278ea58ec',
            'plugins/junit-platform-commons-1.3.1.jar' : '67b7edddfac1935e6e6d9b58d7c7df6db59b1d39',
            'plugins/junit-platform-engine-1.3.1.jar' : '3ee68a06bbdab157dd260e2095c356481d6cd172',
            'plugins/junit-platform-launcher-1.3.1.jar' : '8a07cb776e5aeb320b051183dc8ff142650ddb4e',
            'plugins/jzlib-1.1.3.jar' : 'c01428efa717624f7aabf4df319939dda9646b2d',
            'plugins/maven-aether-provider-3.0.4.jar' : '80eaf6efa354082894adb29fb7db24313977c7f5',
            'plugins/maven-artifact-3.0.4.jar' : '990f82878d95fe79e99659f07ba5b472444fc72e',
            'plugins/maven-compat-3.0.4.jar' : '14d626e1e29a36f5f4629b689e964f8665707839',
            'plugins/maven-core-3.0.4.jar' : '4d60ad977827c011322928c4cedf021575fa39ec',
            'plugins/maven-model-3.0.4.jar' : '5e149cfe15daedebbb1e8970d6a5ff1bea61b94c',
            'plugins/maven-model-builder-3.0.4.jar' : '4f9c6ecf6d6de41933e82a122019117ea0741314',
            'plugins/maven-plugin-api-3.0.4.jar' : '1cd908e0aa67ad2d2b470c00c6db610db60b966b',
            'plugins/maven-repository-metadata-3.0.4.jar' : 'a5c737d02ab9365d272a1d0fc724420808ab4df8',
            'plugins/maven-settings-3.0.4.jar' : '09897b492f19f4a9a37c008c025691cd4a858cdc',
            'plugins/maven-settings-builder-3.0.4.jar' : '1c47e99b3cef4aed391f6c76aa073f3f7f25044b',
            'plugins/nekohtml-1.9.22.jar' : '4f54af68ecb345f2453fb6884672ad08414154e3',
            'plugins/opentest4j-1.1.1.jar' : 'efd9f971e91074491ea55b19f67b13470cf4fcdd',
            'plugins/org.eclipse.jgit-5.0.3.201809091024-r.jar' : '0afec2df3ff8835bc4d5c279d14fad0daae6dd93',
            'plugins/plexus-cipher-1.7.jar' : '51460409b6cdc2b828540c19c05691f89141edc2',
            'plugins/plexus-classworlds-2.5.1.jar' : '98fea8e8c3fb0e8670a69ad6ea445872c9972910',
            'plugins/plexus-component-annotations-1.5.5.jar' : 'c72f2660d0cbed24246ddb55d7fdc4f7374d2078',
            'plugins/plexus-container-default-1.7.1.jar' : 'c10eaacd916f4ae5f1741d04e5841e854d6f7a1e',
            'plugins/plexus-interpolation-1.14.jar' : 'c88dd864fe8b8256c25558ce7cd63be66ba07693',
            'plugins/plexus-sec-dispatcher-1.3.jar' : 'dedc02034fb8fcd7615d66593228cb71709134b4',
            'plugins/plexus-utils-3.1.0.jar' : '60eecb6f15abdb1c653ad80abaac6fe188b3feaa',
            'plugins/pmaven-common-0.8-20100325.jar' : 'f9b22bb94a326507a48fbe9a733882e972fbd31c',
            'plugins/pmaven-groovy-0.8-20100325.jar' : '23111ec4d6ba62882760d8a7c7c12bb3ffcd1f13',
            'plugins/rhino-1.7.10.jar' : '24650bd98b1041df2eb1be5cb64dd1ad5b2e7c55',
            'plugins/simple-4.1.21.jar' : '3464b9fb0221d1a2593894f93278b0129025b4ba',
            'plugins/snakeyaml-1.17.jar' : '7a27ea250c5130b2922b86dea63cbb1cc10a660c',
            'plugins/testng-6.3.1.jar' : '63749361a529df2ca9b1a90adc7cebeca9800e8c',
            'plugins/wagon-file-3.0.0.jar' : '8c2decb84fb1df443f3e21367ede355c90184749',
            'plugins/wagon-http-3.0.0.jar' : 'a4d96c5224b412b049cf8f99f88f5c1ccff8293e',
            'plugins/wagon-http-shared-3.0.0.jar' : 'b343bc92a74066bac02eb1d843b90e0943e6839d',
            'plugins/wagon-provider-api-3.0.0.jar' : 'f160b044ff0a4095170dc4dac75d69065b7c0a8d',
            'plugins/xbean-reflect-3.7.jar' : '6072a967ec936b3bb25b421d8eca07dd750219fd',
            'plugins/xercesImpl-2.12.0.jar' : 'f02c844149fd306601f20e0b34853a670bef7fa2',
            'slf4j-api-1.7.25.jar' : 'da76ca59f6a57ee3102f8f9bd9cee742973efa8a',
            'trove4j-1.0.20181211.jar' : '216c2e14b070f334479d800987affe4054cd563f',
            'xml-apis-1.4.01.jar' : '3789d9fada2d3d458c4ba2de349d48780f381ee3',
        ]

        def libDir = unpackDistribution().file('lib')
        def jars = collectJars(libDir)
        Map<String, TestFile> depJars = jars.collectEntries { [(libDir.relativePath(it)): it] }.findAll { String k, File v -> !k.startsWith('gradle-') && !k.startsWith("plugins/gradle-") && !excluded.contains(k) }

        def added = depJars.keySet() - expectedHashes.keySet()
        def removed = expectedHashes.keySet() - depJars.keySet()

        expect:
        if (!(added + removed).isEmpty()) {
            System.err.println "Dependencies changed: added=$added, removed=$removed"
            printScript(depJars)
            assert (added + removed).isEmpty()
        }

        def errors = []
        depJars.each { String jarPath, TestFile jar ->
            def expected = expectedHashes[jarPath]
            def actual = jar.sha1Hash
            if (expected != actual) {
                errors.add([path: jarPath, expectedHash: expected, actualHash: actual])
            }
        }

        !errors.findAll { error ->
            System.err.println "Sha-1 mismatch for ${error.path}: expected=${error.expectedHash}, actual=${error.actualHash}"
            true
        }
    }

    private static def collectJars(TestFile file, Collection<File> acc = []) {
        if (file.name.endsWith('.jar')) {
            acc.add(file)
        }
        if (file.isDirectory()) {
            file.listFiles().each { f -> collectJars(f, acc) }
        }
        acc
    }

    private static void printScript(depJars) {
        System.err.println "Use the following script to regenerate the expected hashes"
        System.err.println "(note: install the jq package: `brew install jq`)\n"

        System.err.println '#!/bin/bash'
        depJars.each { String jarName, File jar ->
            System.err.println """echo -n "'"; echo -n $jarName; echo -n "' : "; wget -qO - `curl -s -H "X-Artifactory-Override-Base-Url: https://dev12.gradle.org/artifactory" https://dev12.gradle.org/artifactory/api/search/artifact?name=$jar.name | jq '.results[0].uri' | tr -d '\"'` | jq '.checksums.sha1' | tr  -d '"' | sed -e "s/\\(.*\\)/'\\1',/"  """
        }
    }
}
