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

package org.gradle.internal.scopeids


import org.gradle.cache.ObjectHolder
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.Factory
import org.gradle.internal.id.UniqueId
import spock.lang.Specification

class PersistentScopeIdLoaderTest extends Specification {
    def projectCacheDirDir = Mock(File)
    def globalScopedCacheBuilderFactory = Mock(GlobalScopedCacheBuilderFactory)
    def buildTreeScopedCacheBuilderFactory = Mock(BuildTreeScopedCacheBuilderFactory)
    def storeFile = Mock(File)
    def storeFactory = Mock(PersistentScopeIdStoreFactory)
    def idFactory = Mock(Factory) as Factory<UniqueId>
    def store = Mock(ObjectHolder)

    def loader = new DefaultPersistentScopeIdLoader(globalScopedCacheBuilderFactory, buildTreeScopedCacheBuilderFactory, storeFactory, idFactory)

    def "generates user ID in expected location"() {
        given:
        def newId = UniqueId.generate()

        when:
        def id = loader.user.id

        then:
        1 * globalScopedCacheBuilderFactory.baseDirForCrossVersionCache("user-id.txt") >> storeFile

        and:
        1 * storeFactory.create(storeFile, _) >> store

        and:
        1 * store.maybeUpdate(_) >> { ObjectHolder.UpdateAction action ->
            action.update(null)
        }

        and:
        idFactory.create() >> newId

        and:
        id == newId
    }

    def "generates workspace ID in expected location"() {
        given:
        def newId = UniqueId.generate()

        when:
        def id = loader.workspace.id

        then:
        1 * buildTreeScopedCacheBuilderFactory.baseDirForCrossVersionCache("workspace-id.txt") >> storeFile

        and:
        1 * storeFactory.create(storeFile, _) >> store

        and:
        1 * store.maybeUpdate(_) >> { ObjectHolder.UpdateAction action ->
            action.update(null)
        }

        and:
        idFactory.create() >> newId

        and:
        id == newId
    }

    def "returns existing ID if present"() {
        given:
        def existingId = UniqueId.generate()

        when:
        def id = loader.workspace.id

        then:
        1 * buildTreeScopedCacheBuilderFactory.baseDirForCrossVersionCache("workspace-id.txt") >> storeFile

        and:
        1 * storeFactory.create(storeFile, _) >> store

        and:
        1 * store.maybeUpdate(_) >> { ObjectHolder.UpdateAction action ->
            action.update(existingId)
        }

        and:
        0 * idFactory.create()

        and:
        id == existingId
    }

}
