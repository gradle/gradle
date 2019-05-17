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
        def excluded = ['gradle-', 'fastutil-8.2.1-min', 'kotlin-compiler-embeddable-1.3.31-patched']

        def expectedHashes = [
            'annotations-13.0.jar' : 'ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478',
            'ant-1.9.14.jar' : '37c10bafb295aef7a8618da2ff82f0dd2d0cbe2bbbb7d5ea994ea615238bd041',
            'ant-launcher-1.9.14.jar' : 'faaf02c32f0649a2ebd146166b96a5c80cfddd3934fa0c260267e9c5be96088d',
            'asm-7.1.jar' : '4ab2fa2b6d2cc9ccb1eaa05ea329c407b47b13ed2915f62f8c4b8cc96258d4de',
            'asm-analysis-7.1.jar' : '4612c0511a63db2a2570f07ad1959e19ed8eb703e4114da945cb85682519a55c',
            'asm-commons-7.1.jar' : 'e5590489d8f1984d85bfeabd3b17374c59c28ae09d48ec4a0ebbd01959ecd358',
            'asm-tree-7.1.jar' : 'c0e82b220b0a52c71c7ca2a58c99a2530696c7b58b173052b9d48fe3efb10073',
            'commons-compress-1.18.jar' : '5f2df1e467825e4cac5996d44890c4201c000b43c0b23cffc0782d28a0beb9b0',
            'commons-io-2.6.jar' : 'f877d304660ac2a142f3865badfc971dec7ed73c747c7f8d5d2f5139ca736513',
            'commons-lang-2.6.jar' : '50f11b09f877c294d56f24463f47d28f929cf5044f648661c0f0cfbae9a2f49c',
            'failureaccess-1.0.1.jar' : 'a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26',
            'groovy-all-1.0-2.5.4.jar' : '704d3307616c57234871c4db3a355c3e81ea975db8dac8ee6c9264b91c74d2b7',
            'guava-27.1-android.jar' : '686404f2d1d4d221911f96bd627ff60dac2226a5dfa6fb8ba517073eb97ec0ef',
            'jansi-1.17.1.jar' : 'b2234bfb0d8f245562d64ed9325df6b907093f4daa702c9082d4796db2a2d894',
            'javax.inject-1.jar' : '91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff',
            'jcl-over-slf4j-1.7.25.jar' : '5e938457e79efcbfb3ab64bc29c43ec6c3b95fffcda3c155f4a86cc320c11e14',
            'jsr305-3.0.2.jar' : '766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7',
            'jul-to-slf4j-1.7.25.jar' : '416c5a0c145ad19526e108d44b6bf77b75412d47982cce6ce8d43abdbdbb0fac',
            'kotlin-reflect-1.3.31.jar' : 'a0172daf57e511e8e0df9251b508db8aa6b885cdf0c5849addc9b840db4814f0',
            'kotlin-sam-with-receiver-compiler-plugin-1.3.31.jar' : '1c8903b06c079d47374473a302603734cd427f3c59309aefe05a8207d2be5884',
            'kotlin-script-runtime-1.3.31.jar' : '633692186b292292e41ea60d5170e811845b78aba88e20260ba70f7ce3a3ef32',
            'kotlin-scripting-compiler-embeddable-1.3.31.jar' : '4dff2f683f8ceee0e834aeb0ca2686774da6c010ad1faf671dcaf73f071de954',
            'kotlin-stdlib-1.3.31.jar' : 'f38c84326543e66ed4895b20fb3ea0fca527fd5a040e1f49d0946ecf3d2b3b23',
            'kotlin-stdlib-common-1.3.31.jar' : 'd6e9c54c1e6c4df21be9395de558665544c6bdc8f8076ea7518f089f82cd34fc',
            'kotlin-stdlib-jdk7-1.3.31.jar' : 'dbf77e6a5626d941450fdc59cbfe24165858403c12789749a2497265269859a3',
            'kotlin-stdlib-jdk8-1.3.31.jar' : 'ad6acd219b468a532ac3b3c5aacbfd5db02d0ffcf967e2113e4677e2429490f6',
            'kotlinx-metadata-jvm-0.0.5.jar' : 'e49454af130e066a4e1c31255c5fd9a23f31105324f48e98406325b051638908',
            'kryo-2.24.0.jar' : '7e56b32c635058f9aa2820f88919ab702d029cbcd15285da9992e36cc0ae52f2',
            'log4j-over-slf4j-1.7.25.jar' : 'c84c5ce4bbb661369ccd4c7b99682027598a0fb2e3d63a84259dbe5c0bf1f949',
            'minlog-1.2.jar' : 'a678cb1aa8f5d03d901c992c75741841d98a9bc3d55dad02e84d65315c4e60f2',
            'native-platform-0.17.jar' : '38d67a2ef50dbd9c587c01a9cc63c00b1328290b2aadd9c49597f0757274a64a',
            'native-platform-freebsd-amd64-libcpp-0.17.jar' : '9f1f89b5a61930f124d2753a0e9177b31a98ddd8aad85e28075b503eeafef50a',
            'native-platform-freebsd-amd64-libstdcpp-0.17.jar' : 'f086f1512fd180a8d26918ae3489202b2f7b37b52722ce37ecc71ff2ccb42a8a',
            'native-platform-freebsd-i386-libcpp-0.17.jar' : 'd0d321c55bc6087fa16d670967e9dd43c47941f96b19f2614efa564d3d7bce8d',
            'native-platform-freebsd-i386-libstdcpp-0.17.jar' : 'd52319783a154156feff52f5ba7df2fdc6860b13af1e8c580bceec7053880808',
            'native-platform-linux-amd64-0.17.jar' : 'da8eae2338e8dbc25bb88678d247f2ba948870b42e8ce63198b6f714eb3452b3',
            'native-platform-linux-amd64-ncurses5-0.17.jar' : 'd85e190ac044d96ec10058e9546dcb17033ebd68d4db059ab78a538c4838c9e5',
            'native-platform-linux-amd64-ncurses6-0.17.jar' : 'dbedcb909e3968e3e83cf219493fcdf7c0a40f944d9da6e852841303fbb98ce1',
            'native-platform-linux-i386-0.17.jar' : '1eaa1b0bb02d2042b96a8dea941e8cd892766af571d1302b0d30328fccc9b2ed',
            'native-platform-linux-i386-ncurses5-0.17.jar' : '01bf87847a6cae5356dc6d36af517f1bf63e380d960b31713f1629b8b77af4de',
            'native-platform-linux-i386-ncurses6-0.17.jar' : 'bbb344ce1bf7f6ae4ff9acffc97865e45dfdf1980bb4c52a487b1368c3958d0e',
            'native-platform-osx-amd64-0.17.jar' : 'c8be647bd5ae084f91dde3545dede65e5abdac966f219881b1b33def007cb3ab',
            'native-platform-windows-amd64-0.17.jar' : '56573ba9a1f14293135aeb80b7bb891ce316eb1d2485766049dc75cf25c04373',
            'native-platform-windows-amd64-min-0.17.jar' : '40351288397b7688f8472ee94d9588fd90ea545db88dcfd82c288663f486dae0',
            'native-platform-windows-i386-0.17.jar' : 	'1af6bc3dabc85f27195a477ccb6ce05f631354c8ab2d3e213bce9996d3d97992',
            'native-platform-windows-i386-min-0.17.jar' : '5ad72f4ea8990bcd9c0ca3503e0a70cc13ce9c7872556429d2372986ed2a1d69',
            'objenesis-2.6.jar' : '5e168368fbc250af3c79aa5fef0c3467a2d64e5a7bd74005f25d8399aeb0708d',
            'plugins/aether-api-1.13.1.jar' : 'ae8dc80232771f8913febfa410c5719e9ba8ded81fb99788e214fd676dbbe13f',
            'plugins/aether-connector-wagon-1.13.1.jar' : 'df54e8505104228ee7e3fbdead7a7a9cb753b04ca6c9cf60a6b19aee0737f1ec',
            'plugins/aether-impl-1.13.1.jar' : '865511994805827e88f327944a089142bb7f3d88cde271ba3dceb732cb137a93',
            'plugins/aether-spi-1.13.1.jar' : 'd5de4e299be5a79feb1dbe8ff3814034c6e44314b4c00b92ffa8d97576ded5b3',
            'plugins/aether-util-1.13.1.jar' : '687799a0ce988bee9e8eb9ae0ba870300adc0114248ad4a4327bdb625d27e010',
            'plugins/apiguardian-api-1.0.0.jar' : '1f58b77470d8d147a0538d515347dd322f49a83b9e884b8970051160464b65b3',
            'plugins/asm-util-7.1.jar' : 'a24485517596ae1003dcf2329c044a2a861e5c25d4476a695ccaacf560c74d1a',
            'plugins/aws-java-sdk-core-1.11.407.jar' : '91db6485e6b13fffbaafdf127c1dd89bbf127556d4f2ce3c958516c99356dca9',
            'plugins/aws-java-sdk-kms-1.11.407.jar' : 'ffd62931c14e7c27180c93e26808f2dcb8e5aaf9647c16f33c01009028091ae3',
            'plugins/aws-java-sdk-s3-1.11.407.jar' : '15255fde9a9acbe226109c6767bc37d7415beeae2cca959bccf12e939da53280',
            'plugins/bcpg-jdk15on-1.61.jar' : 'd31561762756bdc8b70be5c1c72d9e972914e5549eaffe25e684b73aa15d1f63',
            'plugins/bcprov-jdk15on-1.61.jar' : 'dba6e408f205215ad1a89b70b37353d3cdae4ec61037e1feee885704e2413458',
            'plugins/biz.aQute.bndlib-4.0.0.jar' : 'd1a328c8f63aea4f7ce6028a49255137664a7138fadc4af9d25461192b71e098',
            'plugins/bsh-2.0b6.jar' : 'a17955976070c0573235ee662f2794a78082758b61accffce8d3f8aedcd91047',
            'plugins/commons-codec-1.11.jar' : 'e599d5318e97aa48f42136a2927e6dfa4e8881dff0e6c8e3109ddbbff51d7b7d',
            'plugins/dd-plist-1.21.jar' : '019c61abd93ecf614e3d214e9fed942dcf47d9d2d9548fe59d70f0840ba32fb6',
            'plugins/google-api-client-1.25.0.jar' : '24e1a69d6c04e6e72e3e16757d46d32daa7dd43cb32c3895f832f25358be1402',
            'plugins/google-api-services-storage-v1-rev136-1.25.0.jar' : 'c517a94e5ff2670470fc8c3ae690e7d0d28d210276e6f2228aaafead994c59d1',
            'plugins/google-http-client-1.25.0.jar' : 'fb7d80a515da4618e2b402e1fef96999e07621b381a5889ef091482c5a3e961d',
            'plugins/google-http-client-jackson2-1.25.0.jar' : 'f9e7e0d318860a2092d70b56331976280c4e9348a065ede3b99c92aa032fd853',
            'plugins/google-oauth-client-1.25.0.jar' : '7e2929133d4231e702b5956a7e5dc8347a352acc1e97082b40c3585b81cd3501',
            'plugins/gson-2.8.5.jar' : '233a0149fc365c9f6edbd683cfe266b19bdc773be98eabdaf6b3c924b48e7d81',
            'plugins/hamcrest-core-1.3.jar' : '66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9',
            'plugins/httpclient-4.5.6.jar' : 'c03f813195e7a80e3608d0ddd8da80b21696a4c92a6a2298865bf149071551c7',
            'plugins/httpcore-4.4.10.jar' : '78ba1096561957db1b55200a159b648876430342d15d461277e62360da19f6fd',
            'plugins/ion-java-1.0.2.jar' : '0d127b205a1fce0abc2a3757a041748651bc66c15cf4c059bac5833b27d471a5',
            'plugins/ivy-2.3.0.jar' : 'ff3543305c62f23d1a4cafc66fab9c9f55ea169ccf2b6c040d3fa23254b86b18',
            'plugins/jackson-annotations-2.9.8.jar' : 'fdca896161766ca4a2c3e06f02f6a5ede22a5b3a55606541cd2838eace08ca23',
            'plugins/jackson-core-2.9.8.jar' : 'd934dab0bd48994eeea2c1b493cb547158a338a80b58c4fbc8e85fb0905e105f',
            'plugins/jackson-databind-2.9.8.jar' : '2351c3eba73a545db9079f5d6d768347ad72666537362c8220fe3e950a55a864',
            'plugins/jatl-0.2.3.jar' : '93ee3810f7275244f5f6311d50c587d8fe43ab5047dfa3291aa96fe50587501c',
            'plugins/jaxb-impl-2.3.1.jar' : 'e6c9e0f1830fd5f7c30c25ecf5e2046c5668b06d304add89d2f027d5072297d0',
            'plugins/jcifs-1.3.17.jar' : '3b077938f676934bc20bde821d1bdea2cd6d55d96151218b40fd159532f45061',
            'plugins/jcommander-1.72.jar' : 'e0de160b129b2414087e01fe845609cd55caec6820cfd4d0c90fabcc7bdb8c1e',
            'plugins/jmespath-java-1.11.407.jar' : '0048fd27f16c465011b76443c4daa636fa0fbccf1e48fae3f8d8e0dc7f2f7cf2',
            'plugins/joda-time-2.10.jar' : 'c4d50dae4d58c3031475d64ae5eafade50f1861ca1553252aa7fd176d56e4eec',
            'plugins/jsch-0.1.54.jar' : '92eb273a3316762478fdd4fe03a0ce1842c56f496c9c12fe1235db80450e1fdb',
            'plugins/junit-4.12.jar' : '59721f0805e223d84b90677887d9ff567dc534d7c502ca903c0c2b17f05c116a',
            'plugins/junit-platform-commons-1.3.1.jar' : '457d8e1c0c80d1e320a792ec35e7c180694ba05182d7ecf7dabdb4e5a8a12fe2',
            'plugins/junit-platform-engine-1.3.1.jar' : '303d0546c3e950cc3beaca00dfcbf2632d4eca530e4e446391bf193cbc2a71a3',
            'plugins/junit-platform-launcher-1.3.1.jar' : '6f698076c33cf27d2b80de11506031b625733aab7f18e2d4c5d15803f27231dd',
            'plugins/jzlib-1.1.3.jar' : '89b1360f407381bf61fde411019d8cbd009ebb10cff715f3669017a031027560',
            'plugins/maven-aether-provider-3.0.4.jar' : '33ff4aabbd0d02e4dd8279cda8f366c69915302bc4bb97bc01814a985f5c0643',
            'plugins/maven-artifact-3.0.4.jar' : '3c199a96af9550872724f41c053d7839dfcc6512e7704fa16c675363c4146796',
            'plugins/maven-compat-3.0.4.jar' : 'a2d02378d413d9e87833780820163902c5be4b0f3c44b65de7608f47cd94599f',
            'plugins/maven-core-3.0.4.jar' : '3dd795c0ad9742a0be65a2a5ec22428d59dd2a891a7565ae94f64661e3740528',
            'plugins/maven-model-3.0.4.jar' : '26b6825ea73ac4d7b1a6f5e62ac1c11b0fc272504da6dde9ba8f894cd847e1c1',
            'plugins/maven-model-builder-3.0.4.jar' : 'b4f1d3ae53c290e1ae45694c5ce2d17bf8d577ff5ece0f9aa0cffe151a6ef4e7',
            'plugins/maven-plugin-api-3.0.4.jar' : '4e5ee7f7ab7e43f691788489e59f2da4a322e3e35f2a2d8b714ad929f624eead',
            'plugins/maven-repository-metadata-3.0.4.jar' : 'a25c4db27cffda9e9229db168b1190d6a3e5439f3f67d6afec3df9470e0752d5',
            'plugins/maven-settings-3.0.4.jar' : '3e3df17f5df5e4ce1e7b7f2011c57d61d328e65678542ade2048f0d0fa295f09',
            'plugins/maven-settings-builder-3.0.4.jar' : 'a38a54ec1e69a30ddfc14434e0aec2764fc268668abcc0e132d86692a5dce3e4',
            'plugins/nekohtml-1.9.22.jar' : '452978e8b6667c7b8357fd3f0a2f2f405e4560a7148143a69181735da5d19045',
            'plugins/opentest4j-1.1.1.jar' : 'f106351abd941110226745ed103c85863b3f04e9fa82ddea1084639ae0c5336c',
            'plugins/org.eclipse.jgit-5.0.3.201809091024-r.jar' : '5b3a2dacf8619884bf2ca998c0d7a9980aeaf24ace664e9c2b1adbee8e913e19',
            'plugins/plexus-cipher-1.7.jar' : '114859861ff10f987b880d6f34e3215274af3cc92b3a73831c84d596e37c6511',
            'plugins/plexus-classworlds-2.5.1.jar' : 'de9ce33b29088c2db7c3f55ddc061c2a7a72f9c93c28faad62cc15aee26b6888',
            'plugins/plexus-component-annotations-1.5.5.jar' : '4df7a6a7be64b35bbccf60b5c115697f9ea3421d22674ae67135dde375fcca1f',
            'plugins/plexus-container-default-1.7.1.jar' : 'f3f61952d63675ef61b42fa4256c1635749a5bc17668b4529fccde0a29d8ee19',
            'plugins/plexus-interpolation-1.14.jar' : '7fc63378d3e84663619b9bedace9f9fe78b276c2be3c62ca2245449294c84176',
            'plugins/plexus-sec-dispatcher-1.3.jar' : '3b0559bb8432f28937efe6ca193ef54a8506d0075d73fd7406b9b116c6a11063',
            'plugins/plexus-utils-3.1.0.jar' : '0ffa0ad084ebff5712540a7b7ea0abda487c53d3a18f78c98d1a3675dab9bf61',
            'plugins/pmaven-common-0.8-20100325.jar' : '129306abcdb337cd53e710aadafaa226f017997e6bc81c2cb50deb00916ca8d8',
            'plugins/pmaven-groovy-0.8-20100325.jar' : '87891a373d079fd370e859b1f5bd0579d8b4a68e515fa8bbae91301c28f48d57',
            'plugins/rhino-1.7.10.jar' : '38eb3000cf56b8c7559ee558866a768eebcbf254174522d6404b7f078f75c2d4',
            'plugins/simple-4.1.21.jar' : '8e6439fcc1f2fe87b5dfc20eb13289acba8047e9cfadf45ad3dc5c57a9bcd054',
            'plugins/snakeyaml-1.17.jar' : '5666b36f9db46f06dd5a19d73bbff3b588d5969c0f4b8848fde0f5ec849430a5',
            'plugins/testng-6.3.1.jar' : '57ed8e83e357c3838daad81b789a2753227fee8cfb86aa31a61b765c4093d6ad',
            'plugins/wagon-file-3.0.0.jar' : '63324036899559f94154e6f845e62453a1f202c1b21b80060f2d88a05a05a272',
            'plugins/wagon-http-3.0.0.jar' : '9f68fed6684c2245a62d5d3c8b4954b882562797e96b15ce0f18514c543fb999',
            'plugins/wagon-http-shared-3.0.0.jar' : '40c51265b3ed90b6919c08dcdf3620dc614894ef60bacdf4c34bdbe295b44c49',
            'plugins/wagon-provider-api-3.0.0.jar' : '04de4d2f39178998ef3ce5f6d91a358363ad3f5270e897d5547321ea69fa2992',
            'plugins/xbean-reflect-3.7.jar' : '104e5e9bb5a669f86722f32281960700f7ec8e3209ef51b23eb9b6d23d1629cb',
            'plugins/xercesImpl-2.12.0.jar' : 'b50d3a4ca502faa4d1c838acb8aa9480446953421f7327e338c5dda3da5e76d0',
            'slf4j-api-1.7.25.jar' : '18c4a0095d5c1da6b817592e767bb23d29dd2f560ad74df75ff3961dbde25b79',
            'trove4j-1.0.20181211.jar' : 'affb7c85a3c87bdcf69ff1dbb84de11f63dc931293934bc08cd7ab18de083601',
            'xml-apis-1.4.01.jar' : 'a840968176645684bb01aed376e067ab39614885f9eee44abe35a5f20ebe7fad',
        ]

        def libDir = unpackDistribution().file('lib')
        def jars = collectJars(libDir)
        def filtered = jars.grep { jar ->
            // Filter out any excluded jars
            !excluded.any { jar.name.startsWith(it) }
        }
        Map<String, TestFile> depJars = filtered.collectEntries { [libDir.relativePath(it), it] }

        def added = depJars.keySet() - expectedHashes.keySet()
        def removed = expectedHashes.keySet() - depJars.keySet()

        expect:
        assert (added + removed).isEmpty()

        def errors = []
        depJars.each { String jarPath, TestFile jar ->
            def expected = expectedHashes[jarPath]
            def actual = jar.sha256Hash
            if (expected != actual) {
                errors << "SHA-256 hash does not match for ${jarPath}: expected=${expected}, actual=${actual}"
            }
        }

        assert errors.empty
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
}
