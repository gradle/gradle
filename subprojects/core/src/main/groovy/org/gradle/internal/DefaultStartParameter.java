/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal;

import org.gradle.StartParameter;

public class DefaultStartParameter extends StartParameter implements GradleBuildEnvironment {
    private boolean longLivingProcess;

    public void setLongLivingProcess(boolean longLivingProcess) {
        this.longLivingProcess = longLivingProcess;
    }

    public boolean isLongLivingProcess() {
        return longLivingProcess;
    }

    public DefaultStartParameter newInstance() {
        DefaultStartParameter out = new DefaultStartParameter();
        super.prepareNewInstance(out);
        out.setLongLivingProcess(longLivingProcess);
        return out;
    }

    public DefaultStartParameter newBuild() {
        DefaultStartParameter out = new DefaultStartParameter();
        super.prepareNewBuild(out);
        out.setLongLivingProcess(longLivingProcess);
        return out;
    }
}
