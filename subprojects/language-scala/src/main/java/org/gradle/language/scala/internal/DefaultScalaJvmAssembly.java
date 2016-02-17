/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.scala.internal;

import org.gradle.jvm.internal.DefaultJvmAssembly;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;

public class DefaultScalaJvmAssembly extends DefaultJvmAssembly implements ScalaJvmAssembly {
    private ScalaPlatform scalaPlatform;

    public DefaultScalaJvmAssembly(ComponentSpecIdentifier identifier) {
        super(identifier, ScalaJvmAssembly.class);
    }

    @Override
    public ScalaPlatform getScalaPlatform() {
        return scalaPlatform;
    }

    public void setScalaPlatform(ScalaPlatform scalaPlatform) {
        this.scalaPlatform = scalaPlatform;
    }
}
