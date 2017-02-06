/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transfer;

import org.gradle.api.Action;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationFinishHandle;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.IOException;
import java.io.InputStream;

public class BuildOperationAwareExternalResource implements ExternalResourceReadResponse {
    private final ExternalResourceReadResponse delegate;
    private final BuildOperationFinishHandle<ExternalResourceReadResponse> handle;

    public BuildOperationAwareExternalResource(BuildOperationFinishHandle<ExternalResourceReadResponse> handle) {
        this.delegate = handle.get();
        this.handle = handle;
    }

    @Override
    public InputStream openStream() throws IOException {
        return new BuildOperationAwareInputStream(delegate.openStream(), handle);

    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        return delegate.getMetaData();
    }

    @Override
    public boolean isLocal() {
        return delegate.isLocal();
    }

    @Override
    public void close() throws IOException {
//        handle.finish(new Action<BuildOperationContext>() {
//            @Override
//            public void execute(BuildOperationContext context) {
//                try {
                    delegate.close();
//                } catch (IOException e) {
//                    context.failed(e);
//                }
//            }
//        });
    }


    protected static class BuildOperationAwareInputStream extends InputStream {
        private final InputStream delegate;
        private final BuildOperationFinishHandle<ExternalResourceReadResponse> handle;

        public BuildOperationAwareInputStream(InputStream delegate, BuildOperationFinishHandle<ExternalResourceReadResponse> handle) {
            this.delegate = delegate;
            this.handle = handle;
        }

        @Override
        public void close() throws IOException {
            handle.finish(new Action<BuildOperationContext>() {
                @Override
                public void execute(BuildOperationContext context) {
                    try {
                        delegate.close();
                    } catch (IOException e) {
                        context.failed(e);
                    }
                }
            });
        }

        @Override
        public int read() throws IOException {
            try {
                return delegate.read();
            } catch (IOException e) {
                finishWithError(e);
                throw e;
            }
        }


        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return delegate.read(b, off, len);
            } catch (IOException e) {
                finishWithError(e);
                throw e;
            }
        }


        private void finishWithError(final IOException e) {
            handle.finish(new Action<BuildOperationContext>() {
                @Override
                public void execute(BuildOperationContext context) {
                    context.failed(e);
                }
            });;


        }
    }
}
