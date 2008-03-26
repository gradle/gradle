/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.api.internal.project

/**
 * @author Hans Dockter
 */
class EmbeddedBuildScriptFinder extends BuildScriptFinder {
    static final String EMBEDDED_SCRIPT_NAME = 'Embeddedscript'

    String embeddedScriptText

    EmbeddedBuildScriptFinder(String embeddedScriptText) {
        this.embeddedScriptText = embeddedScriptText
    }

    String getBuildScript(DefaultProject project) {
        return embeddedScriptText
    }

    String getBuildFileName() {
        return EMBEDDED_SCRIPT_NAME
    }
    

}
