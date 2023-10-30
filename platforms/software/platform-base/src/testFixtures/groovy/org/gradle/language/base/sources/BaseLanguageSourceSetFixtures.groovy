/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base.sources


import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.test.fixtures.BaseInstanceFixtureSupport
import org.gradle.util.TestUtil

class BaseLanguageSourceSetFixtures {
    static <T extends LanguageSourceSet> T create(Class<T> publicType, Class<? extends BaseLanguageSourceSet> implType, String name) {
        return BaseInstanceFixtureSupport.create(publicType, LanguageSourceSetInternal, implType, name) { MutableModelNode node ->
            BaseLanguageSourceSet.create(publicType, implType, new DefaultComponentSpecIdentifier("project", name), TestUtil.objectFactory())
        }
    }
}
