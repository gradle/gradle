/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.publication.maven.internal

import org.sonatype.aether.connector.wagon.WagonProvider
import org.apache.maven.wagon.Wagon
import org.apache.maven.wagon.providers.http.LightweightHttpWagon

/**
 * Stolen from eather demo...
 *
 * @author: Szczepan Faber, created at: 5/11/11
 */
class ManualWagonProvider implements WagonProvider {
    Wagon lookup(String roleHint) {
        if ("http".equals(roleHint)) {
            new LightweightHttpWagon()
        }
        null
    }

    void release(Wagon wagon) {}
}
