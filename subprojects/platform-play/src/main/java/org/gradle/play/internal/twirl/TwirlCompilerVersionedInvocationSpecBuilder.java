/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.twirl;

import java.io.Serializable;

public class TwirlCompilerVersionedInvocationSpecBuilder implements Serializable{

    private ScalaCodecMapper scalaCodecMapper;

    public TwirlCompilerVersionedInvocationSpecBuilder(){
        this(new ScalaCodecMapper());
    }

    TwirlCompilerVersionedInvocationSpecBuilder(ScalaCodecMapper scalaCodecMapper){
        this.scalaCodecMapper = scalaCodecMapper;
    }

    VersionedInvocationSpec build(TwirlCompileSpec spec) {
        VersionedInvocationSpec versionedTwirlCompileInvocationSpec = null;
        switch (TwirlCompilerVersion.parse(spec.getCompilerVersion())) {
            case V_22X:
                versionedTwirlCompileInvocationSpec = new VersionedInvocationSpec(
                        TwirlCompilerVersion.V_22X,
                        spec.getSourceDirectory(),
                        spec.getDestinationDir(),
                        getFormatterType(spec, TwirlCompilerVersion.V_22X),
                        spec.getAdditionalImports());
                break;

            case V_102:
                Object scalaCodec = scalaCodecMapper.map(spec.getCodec());
                versionedTwirlCompileInvocationSpec = new VersionedInvocationSpec(
                        TwirlCompilerVersion.V_102,
                        spec.getSourceDirectory(),
                        spec.getDestinationDir(),
                        getFormatterType(spec, TwirlCompilerVersion.V_102),
                        spec.getAdditionalImports(),
                        scalaCodec,
                        spec.isInclusiveDots(),
                        spec.isUseOldParser()
                );
                break;

        }
        return versionedTwirlCompileInvocationSpec;
    }

    private String getFormatterType(TwirlCompileSpec spec, TwirlCompilerVersion compilerVersion) {
        String formatterType = spec.getFormatterType();
        if(formatterType==null){
            formatterType = compilerVersion.getDefaultFormatterType();
        }
        return formatterType;
    }
}
