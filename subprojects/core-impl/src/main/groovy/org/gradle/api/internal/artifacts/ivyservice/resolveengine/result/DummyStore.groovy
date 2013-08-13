package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.internal.cache.Store

class DummyStore implements Store<Object> {
    Object load(org.gradle.internal.Factory<Object> createIfNotPresent) {
        return createIfNotPresent.create();
    }
}
