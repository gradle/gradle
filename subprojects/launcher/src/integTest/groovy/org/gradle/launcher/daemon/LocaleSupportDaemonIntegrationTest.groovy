/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.launcher.daemon

import org.apache.commons.lang.LocaleUtils
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import spock.lang.Issue

@Issue("https://issues.gradle.org/browse/GRADLE-3142")
class LocaleSupportDaemonIntegrationTest extends DaemonIntegrationSpec {

    def locales = [
            LocaleUtils.toLocale("es_MX"),
            LocaleUtils.toLocale("ja_JP"),
            LocaleUtils.toLocale("hr_HR")
    ].findAll {
        it != Locale.default
    }

    def "custom locale is applied to daemon"() {

        buildScript """
            task printLocale {
                doFirst {
                    println "defaultLocale: " + Locale.default
                }
            }
        """

        when:
        runWithLocale locales[0]

        then:
        ranWithLocale locales[0]
        daemons.daemons.size() == 1

        when:
        runWithLocale locales[1]

        then:
        ranWithLocale locales[1]
        daemons.daemons.size() == 2
    }

    def "locale is restored after build"() {
        def startLocale = locales[0]
        def changeLocale = locales[1]

        buildScript """
            task printLocale {
                doFirst {
                    Locale.setDefault(new Locale("$changeLocale.language", "$changeLocale.country", "$changeLocale.variant"))
                    println "defaultLocale: " + Locale.default
                }
            }
        """

        when:
        runWithLocale startLocale

        then:
        ranWithLocale changeLocale
        daemons.daemons.size() == 1

        when:
        runWithLocale startLocale

        then:
        ranWithLocale changeLocale
        daemons.daemons.size() == 1
    }

    void ranWithLocale(Locale locale) {
        assert result.output.contains("defaultLocale: " + locale)
    }

    void runWithLocale(Locale locale) {
        executer.withDefaultLocale(locale)
        run "printLocale"
    }

}
