/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.services

import org.gradle.api.internal.services.PublicServiceLookups.EntryPoint
import spock.lang.Specification

import java.util.regex.Pattern

/**
 * The services available for lookup with {@code service()} are listed by hand in a table in the user guide. This test
 * is the signal that the table and the allowlist in {@link PublicServiceLookups} have drifted apart: it fails naming
 * the service and the scope that differ.
 * <p>
 * If this test fails, the allowlist is the source of truth and the documentation is what needs to change, unless the
 * failure comes from the layout of the table:
 * <ul>
 *     <li>A service was added to, removed from, or moved between scopes in {@code PublicServiceLookups.AVAILABLE_SERVICES}:
 *     edit the matching row of the table in
 *     {@code platforms/documentation/docs/src/docs/userguide/reference/gradle-types/service_injection.adoc}
 *     (section "Services available for lookup"). Also update the table in the release notes of the release that ships
 *     the change, which this test does not check.</li>
 *     <li>The table was reformatted so that a row is no longer a single line
 *     {@code | <<anchor,`ServiceName`>> | ✓ | ✓ | ✓}, or its header row changed: adjust {@link #TABLE_HEADER},
 *     {@link #COLUMNS} and {@link #TABLE_ROW}.</li>
 * </ul>
 * The guide file is declared as an input of the test task in this module's build script, so the test reruns when only
 * the documentation changes; do not remove that wiring.
 */
class PublicServiceLookupDocumentationTest extends Specification {

    private static final String TABLE_HEADER = "| Service | Projects | Tasks | Settings"
    private static final List<EntryPoint> COLUMNS = [EntryPoint.PROJECT, EntryPoint.TASK, EntryPoint.SETTINGS]
    private static final Pattern TABLE_ROW = Pattern.compile(/(?m)^\| <<[^,]+,`(\w+)`>>\s*\|([^\n]*)$/)


    private static final String HOW_TO_FIX = "The allowlist is the source of truth: update the documentation, see the Javadoc of this test"

    def "the table in the user guide matches the allowlist"() {
        given:
        def guide = new File(System.getProperty("org.gradle.services.serviceInjectionGuide"))
        def table = parseTable(guide.getText("UTF-8"))
        def allowlist = PublicServiceLookups.availableServices().collectEntries { type, scopes -> [(type.simpleName): scopes as Set] }

        expect: "the table lists exactly the allowlisted services"
        assert table.keySet() == allowlist.keySet(): "user guide table lists the services ${table.keySet().sort()}, the allowlist has ${allowlist.keySet().sort()}. ${HOW_TO_FIX} (${guide})"

        and: "each row ticks exactly the scopes the service is available in"
        allowlist.every { service, scopes ->
            assert table[service] == scopes: "user guide row '${service}' ticks ${table[service].sort()}, the allowlist has ${scopes.sort()}. ${HOW_TO_FIX} (${guide})"
            true
        }
    }

    private static Map<String, Set<EntryPoint>> parseTable(String adoc) {
        def start = adoc.indexOf(TABLE_HEADER)
        assert start >= 0: "the table of services available for lookup was not found in the user guide"
        def end = adoc.indexOf("|===", start)
        def rows = [:]
        def matcher = TABLE_ROW.matcher(adoc.substring(start, end))
        while (matcher.find()) {
            def cells = matcher.group(2).split(/\|/, -1)*.trim()
            assert cells.size() == COLUMNS.size(): "user guide row '${matcher.group(1)}' has ${cells.size()} scope cells, expected ${COLUMNS.size()}"
            rows[matcher.group(1)] = (0..<COLUMNS.size()).findAll { cells[it].startsWith("✓") }.collect { COLUMNS[it] } as Set
        }
        assert !rows.isEmpty(): "the table of services available for lookup has no rows"
        return rows
    }
}
