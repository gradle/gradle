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

package org.gradle.test.fixtures.maven

import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.TestFile

class HttpArtifact extends HttpResource {

    HttpServer server
    MavenFileModule backingModule
    String moduleRootPath
    final Map options

    public HttpArtifact(HttpServer server, String moduleRootPath, MavenFileModule backingModule, Map<String, ?> options = [:]) {
        this.server = server
        this.moduleRootPath = moduleRootPath
        this.backingModule = backingModule
        this.options = options

    }

    void expectHead() {
        server.expectHead("$moduleVersionPath/${getArtifactFile().name}", getArtifactFile())
    }

    void expectHeadMissing() {
        server.expectHeadMissing("$moduleVersionPath/${getMissingArtifactName()}")
    }

    void expectGet() {
        server.expectGet(getArtifactPath(), backingModule.getArtifactFile(options))
    }

    void expectGetMissing() {
        server.expectGetMissing("$moduleVersionPath/${getMissingArtifactName()}")
    }

    void expectSha1GetMissing() {
        server.expectGetMissing("$moduleVersionPath/${missingArtifactName}.sha1")
    }

    void expectSha1Get() {
        server.expectGet(getArtifactSha1Path(), backingModule.sha1File(getArtifactFile()))
    }

    TestFile getArtifactSha1File() {
        backingModule.artifactSha1File
    }

    String getArtifactSha1Path() {
        "${getArtifactPath()}.sha1"
    }



    protected String getModuleVersionPath() {
        "${moduleRootPath}/${backingModule.version}"
    }

    private String getArtifactPath() {
        "$moduleVersionPath/${getArtifactFile().name}"
    }

    TestFile getArtifactFile() {
        return backingModule.getArtifactFile(options)
    }

    private String getMissingArtifactName() {
        if (backingModule.version.endsWith("-SNAPSHOT")) {
            return "${backingModule.artifactId}-${backingModule.version}${options["classifier"] ? "-" + options["classifier"] : ""}.jar"
        } else {
            return artifactFile.name
        }
    }

}
