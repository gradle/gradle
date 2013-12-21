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
package org.gradle.plugins.signing

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.maven.MavenDeployment
import org.gradle.util.DeprecationLogger

/**
 * @deprecated Use {@link SigningExtension}
 */
@Deprecated
class SigningPluginConvention {
    
    private SigningExtension extension
    
    SigningPluginConvention(SigningExtension extension) {
        this.extension = extension
    }

    /**
     * @deprecated Use {@link SigningExtension#sign(PublishArtifact...) project.signing.sign(PublishArtifact...) }
     */
    @Deprecated
    SignOperation sign(PublishArtifact... publishArtifacts) {
        DeprecationLogger.nagUserOfReplacedMethod("sign()", "signing.sign()")
        extension.sign(publishArtifacts)
    }

    /**
     * @deprecated Use {@link SigningExtension#sign(File...) project.signing.sign(File...) }
     */
    @Deprecated
    SignOperation sign(File... files) {
        DeprecationLogger.nagUserOfReplacedMethod("sign()", "signing.sign()")
        extension.sign(files)
    }
    
    /**
     * @deprecated Use {@link SigningExtension#sign(String, File...) project.signing.sign(String, File...)}
     */
    @Deprecated
    SignOperation sign(String classifier, File... files) {
        DeprecationLogger.nagUserOfReplacedMethod("sign()", "signing.sign()")
        extension.sign(classifier, files)
    }
    
    /**
     * @deprecated Use {@link SigningExtension#sign(Closure) project.signing.sign \{ } }
     */
    @Deprecated
    SignOperation sign(Closure closure) {
        DeprecationLogger.nagUserOfReplacedMethod("sign()", "signing.sign()")
        extension.sign(closure)
    }
    
    /**
     * @deprecated Use {@link SigningExtension#signPom() project.signing.signPom}
     */
    @Deprecated
    Signature signPom(MavenDeployment mavenDeployment, Closure closure = null) {
        DeprecationLogger.nagUserOfReplacedMethod("signPom()", "signing.signPom()")
        extension.signPom(mavenDeployment, closure)
    }
}