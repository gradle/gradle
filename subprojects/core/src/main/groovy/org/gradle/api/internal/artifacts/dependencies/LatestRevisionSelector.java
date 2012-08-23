/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.dependencies;

import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.latest.LatestRevisionStrategy;
import org.gradle.api.Transformer;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Created: 23/08/2012
 *
 * @author Szczepan Faber
 */
public class LatestRevisionSelector {

    public String latest(Collection<String> versions) {
        Collection<DefaultArtifactInfo> infos = CollectionUtils.collect(versions, new LinkedList<DefaultArtifactInfo>(), new Transformer<DefaultArtifactInfo, String>() {
            public DefaultArtifactInfo transform(String version) {
                return new DefaultArtifactInfo(version);
            }
        });
        return Collections.<DefaultArtifactInfo>max(infos, new LatestRevisionStrategy().getComparator()).version;
    }

    //TODO SF there's some overlap with some code in other places
    private static class DefaultArtifactInfo implements ArtifactInfo {

        private String version;

        public DefaultArtifactInfo(String version) {
            this.version = version;
        }

        public String getRevision() {
            return version;
        }

        public long getLastModified() {
            throw new UnsupportedOperationException();
        }
    }
}
