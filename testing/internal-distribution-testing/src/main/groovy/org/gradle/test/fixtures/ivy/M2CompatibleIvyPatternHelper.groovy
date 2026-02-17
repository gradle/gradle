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

package org.gradle.test.fixtures.ivy

import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId

class M2CompatibleIvyPatternHelper {

    static String substitute(String pattern, String organisation, String module, String revision, boolean m2Compatible) {
        def organisationToken = organisationToken(m2Compatible, organisation)
        IvyPatternHelper.substitute(pattern, new ModuleRevisionId(new ModuleId(organisationToken, module), revision))
    }

    private static String organisationToken(boolean m2Compatible, String organisation) {
        m2Compatible ? organisation.replaceAll(/\./, '/') : organisation
    }

    static String substitute(String pattern, boolean m2Compatible, Map<String, String> tokens) {
        if (tokens.containsKey('organisation')) {
            tokens.put('organisation', organisationToken(m2Compatible, tokens.get('organisation')))
        }
        IvyPatternHelper.substituteTokens(pattern, tokens)
    }
}
