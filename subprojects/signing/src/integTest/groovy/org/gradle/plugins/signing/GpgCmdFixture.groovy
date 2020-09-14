/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class GpgCmdFixture {
    private static final Random RANDOM = new Random()
    private static final int ALL_DIGITS_AND_LETTERS_RADIX = 36
    private static final int MAX_RANDOM_PART_VALUE = Integer.valueOf("zzzzz", ALL_DIGITS_AND_LETTERS_RADIX)

    static String createRandomPrefix() {
        return Integer.toString(RANDOM.nextInt(MAX_RANDOM_PART_VALUE), ALL_DIGITS_AND_LETTERS_RADIX)
    }

    static String getAvailableGpg() {
        if (tryRun('gpg2')) {
            return 'gpg2'
        } else if (tryRun('gpg')) {
            return 'gpg'
        } else {
            return null
        }
    }

    static boolean tryRun(String cmd) {
        try {
            "${cmd} --version".execute()
            return true
        }
        catch (IOException e) {
            return false
        }
    }

    static setupGpgCmd(TestFile buildDir) {
        def gpgHomeSymlink = prepareGnupgHomeSymlink(buildDir.file('gnupg-home'))
        Properties properties = new Properties()
        properties.load(buildDir.file('gradle.properties').newInputStream())
        String client = GpgCmdFixture.getAvailableGpg()
        assert client
        properties.put('signing.gnupg.executable', client)
        properties.put('signing.gnupg.useLegacyGpg', (client == 'gpg').toString())
        properties.put('signing.gnupg.homeDir', gpgHomeSymlink.toAbsolutePath().toString())
        properties.remove('signing.gnupg.optionsFile')
        properties.store(buildDir.file('gradle.properties').newOutputStream(), '')

        return gpgHomeSymlink
    }

    static cleanupGpgCmd(def gpgHomeSymlink) {
        Files.deleteIfExists(gpgHomeSymlink)
    }

    static prepareGnupgHomeSymlink(File gpgHomeInTest) {
        // We have to do this, otherwise gpg will complain: can't connect to the agent: File name too long
        // it's limited to 108 chars due to http://man7.org/linux/man-pages/man7/unix.7.html
        Path tmpLink = Paths.get(SystemProperties.getInstance().getJavaIoTmpDir()).resolve(createRandomPrefix())
        return Files.createSymbolicLink(tmpLink, gpgHomeInTest.toPath())
    }
}
