/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.generator

class RepositoryBuilder {
    private int depth = 1
    private int numberOfArtifacts = 0
    private File targetDir
    boolean withSnapshotVersions = false
    private MavenJarCreator mavenJarCreator = new MavenJarCreator()

    public RepositoryBuilder(File targetDir) {
        this.targetDir = targetDir;
    }

    RepositoryBuilder withArtifacts(int numberOfArtifacts) {
        this.numberOfArtifacts = numberOfArtifacts
        return this;
    }

    RepositoryBuilder withDepth(int depth) {
        this.depth = depth
        return this;
    }

    RepositoryBuilder withSnapshotVersions(boolean withSnapshotVersions) {
        this.withSnapshotVersions = withSnapshotVersions
        return this;
    }

    RepositoryBuilder withMavenJarCreator(MavenJarCreator mavenJarCreator) {
        this.mavenJarCreator = mavenJarCreator
        this
    }

    MavenRepository create() {
        if (numberOfArtifacts == 0) {
            return null;
        }
        targetDir.mkdirs();
        MavenRepository repo = new MavenRepository(new File(targetDir, "mavenRepo"))
        repo.mavenJarCreator = mavenJarCreator
        numberOfArtifacts.times {
            if (withSnapshotVersions) {
                repo.addModule('group', "artifact$it", "1.0-SNAPSHOT")
            } else {
                repo.addModule('group', "artifact$it")
            }
        }

        transformGraphToDepth(repo.modules, depth)
        repo.setDepth(depth)
        repo.publish()
        repo
    }

    void transformGraphToDepth(List<MavenModule> modules, int depth) {
        def depGroups = modules.groupBy { (int) ((it.artifactId - "artifact").toInteger() / depth) }
        depGroups.each { idx, groupModules ->
            for (int i = 0; i < groupModules.size() - 1; i++) {
                def next = groupModules[i + 1]
                groupModules[i].dependsOn(next.groupId, next.artifactId, next.version)
            }
        }
    }
}
