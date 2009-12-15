/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.internal.project.StandardOutputRedirector
import org.gradle.api.internal.project.DefaultStandardOutputRedirector
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.configuration.ScriptObjectConfigurerFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.BaseDirConverter

abstract class BasicScript extends org.gradle.groovy.scripts.Script implements org.gradle.api.Script {
    private final StandardOutputRedirector redirector = new DefaultStandardOutputRedirector()
    private ServiceRegistry services
    private Object target
    private FileResolver resolver

    void init(Object target, ServiceRegistry services) {
        new DefaultScriptMetaData().applyMetaData(this, target)
        this.target = target
        this.services = services
        if (scriptSource.sourceFile) {
            resolver = new BaseDirConverter(scriptSource.sourceFile.parentFile)
        } else {
            resolver = new IdentityFileResolver()
        }
    }

    def Object getScriptTarget() {
        return target
    }

    public void apply(Closure closure) {
        ObjectConfigurationAction action = new DefaultObjectConfigurationAction(resolver, services.get(ScriptObjectConfigurerFactory), target)
        ConfigureUtil.configure(closure, action)
        action.execute()
    }

    def String toString() {
        return "script"
    }

    StandardOutputRedirector getStandardOutputRedirector() {
        return redirector
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
