/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.vcs.fixtures;

import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gradle.api.Action;
import org.gradle.internal.ErroringAction;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.server.http.BlockingHttpServer;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.zip.GZIPInputStream;

public class GitHttpRepository implements TestRule, GitRepository {
    private final BlockingHttpServer server;
    private final GitFileRepository backingRepo;

    public GitHttpRepository(BlockingHttpServer server, String name, File parentDirectory) {
        this.server = server;
        this.backingRepo = new GitFileRepository(name, parentDirectory);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return backingRepo.apply(base, description);
    }

    @Override
    public URI getUrl() {
        return server.getUri().resolve("/" + backingRepo.getName());
    }

    @Override
    public TestFile getWorkTree() {
        return backingRepo.getWorkTree();
    }

    @Override
    public TestFile file(Object... path) {
        return backingRepo.file(path);
    }

    @Override
    public RevCommit commit(String message) throws GitAPIException {
        return backingRepo.commit(message);
    }

    @Override
    public Ref checkout(String branchName) throws GitAPIException {
        return backingRepo.checkout(branchName);
    }

    @Override
    public Ref createBranch(String branchName) throws GitAPIException {
        return backingRepo.createBranch(branchName);
    }

    @Override
    public Ref createLightWeightTag(String tagName) throws GitAPIException {
        return backingRepo.createLightWeightTag(tagName);
    }

    public BlockingHttpServer.ExpectedRequest listVersions() {
        return server.get(backingRepo.getName() + "/info/refs", getRefsAction());
    }

    public void expectListVersions() {
        server.expect(listVersions());
    }

    public void expectCloneSomething() {
        server.expect(server.get(backingRepo.getName() + "/info/refs", getRefsAction()));
        server.expect(server.post(backingRepo.getName() + "/git-upload-pack", new ErroringAction<HttpExchange>() {
            @Override
            protected void doExecute(HttpExchange httpExchange) throws Exception {
                httpExchange.getResponseHeaders().add("content-type", "application/x-git-upload-pack-result");
                httpExchange.sendResponseHeaders(200, 0);
                ProcessBuilder builder = new ProcessBuilder();
                final Process process = builder.command("git", "upload-pack", "--stateless-rpc", backingRepo.getWorkTree().getAbsolutePath()).redirectErrorStream(true).start();

                InputStream instream = new GZIPInputStream(httpExchange.getRequestBody());
                ByteArrayOutputStream requestContent = new ByteArrayOutputStream();
                IOUtils.copy(instream, requestContent);
                byte[] bytes = requestContent.toByteArray();
                process.getOutputStream().write(bytes);
                process.getOutputStream().flush();

                IOUtils.copy(process.getInputStream(), httpExchange.getResponseBody());
                int result = process.waitFor();
                if (result != 0) {
                    throw new RuntimeException("Failed to run git upload-pack");
                }
            }
        }));
    }

    private Action<HttpExchange> getRefsAction() {
        return new ErroringAction<HttpExchange>() {
            @Override
            protected void doExecute(HttpExchange httpExchange) throws Exception {
                ProcessBuilder builder = new ProcessBuilder();
                Process process = builder.command("git", "upload-pack", "--advertise-refs", backingRepo.getWorkTree().getAbsolutePath()).redirectErrorStream(true).start();
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                content.write("001e# service=git-upload-pack\n".getBytes());
                content.write("0000".getBytes());
                IOUtils.copy(process.getInputStream(), content);
                process.waitFor();
                int result = process.waitFor();
                if (result != 0) {
                    throw new RuntimeException("Failed to run git upload-pack");
                }

                byte[] bytes = content.toByteArray();
                httpExchange.getResponseHeaders().add("content-type", "application/x-git-upload-pack-advertisement");
                httpExchange.sendResponseHeaders(200, bytes.length);
                httpExchange.getResponseBody().write(bytes);
            }
        };
    }
}
