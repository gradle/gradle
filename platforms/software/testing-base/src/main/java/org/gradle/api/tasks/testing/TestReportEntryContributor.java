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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;

/**
 * Contributes a custom entry to the generic HTML test report.
 *
 * <p>
 * Each contributor can add its own entry on every page of the report that is generated. When a container runs more than
 * once (e.g. in multiple test workers), the contributor is invoked once per run.
 * </p>
 *
 * <p>
 * Report pages are generated in parallel, so contributors may be invoked concurrently from multiple threads. They must
 * therefore be thread-safe.
 * </p>
 *
 * @since 9.10.0
 */
@Incubating
@NullMarked
public interface TestReportEntryContributor {
    /**
     * The name of this contributor's entry.
     *
     * <p>
     * The name:
     * </p>
     * <ul>
     *     <li>must not be blank</li>
     *     <li>must not have leading or trailing whitespace</li>
     *     <li>must be unique among the contributors of a report</li>
     *     <li>
     *         must not clash with the names of the built-in entries, which include:
     *         <ul>
     *             <li>{@code summary}</li>
     *             <li>{@code standard output}</li>
     *             <li>{@code error output}</li>
     *             <li>{@code data}</li>
     *             <li>{@code attachments}</li>
     *         </ul>
     *         More built-in entries may be added in the future.
     *     </li>
     * </ul>
     *
     * @return the entry name
     * @since 9.10.0
     */
    String getEntryName();

    /**
     * Returns the content of the entry for a test.
     *
     * @param context the test to contribute an entry for
     * @return the content of the entry, or {@link TestReportEntryContent#empty()} to not show an entry for this test
     * @since 9.10.0
     */
    TestReportEntryContent contentFor(TestReportEntryContext context);
}
