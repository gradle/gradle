/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.groovy.scripts

import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.logging.StandardOutputCapture

/**
 * @author Hans Dockter
 *
 * todo: We need our own base class as a workaround for http://jira.codehaus.org/browse/GROOVY-2635. When this bug is fixed we can use the metaclass.
 * todo: We don't understand why adding propertyMissing and methodMissing to this class does not work.
 */
abstract class BasicScript extends org.gradle.groovy.scripts.Script implements org.gradle.api.Script, FileOperations {
    private StandardOutputCapture standardOutputCapture
    private Object target

    void init(Object target, ServiceRegistry services) {
        new DefaultScriptMetaData().applyMetaData(this, target)
        standardOutputCapture = services.get(StandardOutputCapture.class)
        this.target = target
    }

    def Object getScriptTarget() {
        return target
    }

    def StandardOutputCapture getStandardOutputCapture() {
        return standardOutputCapture
    }

    void setProperty(String property, newValue) {
        if ("metaClass".equals(property)) {
            setMetaClass((MetaClass) newValue)
        } else if ("scriptTarget".equals(property)) {
            target = newValue
        } else {
            target."$property" = newValue
        }
    }
}
