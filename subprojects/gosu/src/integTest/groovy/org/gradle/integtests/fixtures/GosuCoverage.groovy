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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class GosuCoverage {

    static final boolean USE_SINGLE_VERSION_ONLY = GradleContextualExecuter.isDaemon()

    static final String NEWEST = '1.13.1'

    static final String[] DEFAULT = USE_SINGLE_VERSION_ONLY ? [NEWEST] : ['1.12.1', NEWEST]

    static final String[] OLDER = USE_SINGLE_VERSION_ONLY ? ['1.11'] : ['1.10.1', '1.11']

}
