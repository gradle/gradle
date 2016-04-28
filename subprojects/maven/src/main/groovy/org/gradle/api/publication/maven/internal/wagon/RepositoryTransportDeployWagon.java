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

package org.gradle.api.publication.maven.internal.wagon;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.*;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.resource.Resource;
import org.gradle.api.GradleException;
import org.gradle.internal.resource.local.FileLocalResource;
import org.gradle.internal.resource.local.LocalResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import static org.apache.maven.wagon.events.SessionEvent.*;
import static org.apache.maven.wagon.events.TransferEvent.*;

/**
 * A maven wagon intended to work with {@link org.apache.maven.artifact.manager.DefaultWagonManager} Maven uses reflection to initialize instances of this wagon see: {@link
 * org.codehaus.plexus.component.factory.java.JavaComponentFactory#newInstance(org.codehaus.plexus.component.repository.ComponentDescriptor, org.codehaus.classworlds.ClassRealm,
 * org.codehaus.plexus.PlexusContainer)}
 */
public class RepositoryTransportDeployWagon implements Wagon {

    private static final ThreadLocal<RepositoryTransportWagonAdapter> CURRENT_DELEGATE = new InheritableThreadLocal<RepositoryTransportWagonAdapter>();

    private SessionEventSupport sessionEventSupport = new SessionEventSupport();
    private TransferEventSupport transferEventSupport = new TransferEventSupport();
    private Repository mutatingRepository;

    public static void contextualize(RepositoryTransportWagonAdapter adapter) {
        CURRENT_DELEGATE.set(adapter);
    }

    public static void decontextualize() {
        CURRENT_DELEGATE.remove();
    }

    @Override
    public final void get(String resourceName, File destination) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        Resource resource = new Resource(resourceName);
        this.transferEventSupport.fireTransferInitiated(transferEvent(resource, TRANSFER_INITIATED, REQUEST_GET));
        this.transferEventSupport.fireTransferStarted(transferEvent(resource, TRANSFER_STARTED, REQUEST_GET));
        try {
            if (!destination.exists()) {
                destination.getParentFile().mkdirs();
                destination.createNewFile();
            }
            if (!getDelegate().getRemoteFile(destination, resourceName)) {
                throw new ResourceDoesNotExistException(String.format("Resource '%s' does not exist", resourceName));
            }
            this.transferEventSupport.fireTransferCompleted(transferEvent(resource, TRANSFER_COMPLETED, REQUEST_GET));
        } catch (ResourceDoesNotExistException e) {
            this.transferEventSupport.fireTransferError(transferEvent(resource, e, REQUEST_GET));
            throw e;
        } catch (Exception e) {
            this.transferEventSupport.fireTransferError(transferEvent(resource, e, REQUEST_GET));
            throw new TransferFailedException(String.format("Could not get resource '%s'", resourceName), e);
        }
    }

    @Override
    public final void put(File file, String resourceName) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        Resource resource = new Resource(resourceName);
        this.transferEventSupport.fireTransferInitiated(transferEvent(resource, TRANSFER_INITIATED, REQUEST_PUT));
        try {
            LocalResource localResource = new MavenTransferLoggingFileResource(file, resource);
            getDelegate().putRemoteFile(localResource, resourceName);
        } catch (Exception e) {
            this.transferEventSupport.fireTransferError(transferEvent(resource, e, REQUEST_PUT));
            throw new TransferFailedException(String.format("Could not write to resource '%s'", resourceName), e);
        }
        this.transferEventSupport.fireTransferCompleted(transferEvent(resource, TRANSFER_COMPLETED, REQUEST_PUT));
    }

    private RepositoryTransportWagonAdapter getDelegate() {
        return CURRENT_DELEGATE.get();
    }

    @Override
    public final boolean resourceExists(String resourceName) throws TransferFailedException, AuthorizationException {
        throwNotImplemented("getIfNewer(String resourceName, File file, long timestamp)");
        return false;
    }

    @Override
    public final boolean getIfNewer(String resourceName, File file, long timestamp) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        throwNotImplemented("getIfNewer(String resourceName, File file, long timestamp)");
        return false;
    }

    @Override
    public final void putDirectory(File file, String resourceName) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        throwNotImplemented("putDirectory(File file, String resourceName)");
    }

    @Override
    public final List getFileList(String resourceName) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        throwNotImplemented("getFileList(String resourceName)");
        return null;
    }

    @Override
    public final boolean supportsDirectoryCopy() {
        return false;
    }

    @Override
    public final Repository getRepository() {
        return this.mutatingRepository;
    }

    @Override
    public final void openConnection() throws ConnectionException, AuthenticationException {
    }

    @Override
    public final void connect(Repository repository) throws ConnectionException, AuthenticationException {
        this.mutatingRepository = repository;
        this.sessionEventSupport.fireSessionLoggedIn(sessionEvent(SESSION_LOGGED_IN));
        this.sessionEventSupport.fireSessionOpened(sessionEvent(SESSION_OPENED));
    }

    @Override
    public final void connect(Repository repository, ProxyInfo proxyInfo) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void connect(Repository repository, ProxyInfoProvider proxyInfoProvider) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void connect(Repository repository, AuthenticationInfo authenticationInfo) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void connect(Repository repository, AuthenticationInfo authenticationInfo, ProxyInfo proxyInfo) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void connect(Repository repository, AuthenticationInfo authenticationInfo, ProxyInfoProvider proxyInfoProvider) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void disconnect() throws ConnectionException {
        this.sessionEventSupport.fireSessionDisconnecting(sessionEvent(SESSION_DISCONNECTING));
        this.sessionEventSupport.fireSessionLoggedOff(sessionEvent(SESSION_LOGGED_OFF));
        this.sessionEventSupport.fireSessionDisconnected(sessionEvent(SESSION_LOGGED_OFF));
    }

    @Override
    public final void addSessionListener(SessionListener sessionListener) {
        this.sessionEventSupport.addSessionListener(sessionListener);
    }

    @Override
    public final void removeSessionListener(SessionListener sessionListener) {
        this.sessionEventSupport.removeSessionListener(sessionListener);
    }

    @Override
    public final boolean hasSessionListener(SessionListener sessionListener) {
        return this.sessionEventSupport.hasSessionListener(sessionListener);
    }

    @Override
    public final void addTransferListener(TransferListener transferListener) {
        this.transferEventSupport.addTransferListener(transferListener);
    }

    @Override
    public final void removeTransferListener(TransferListener transferListener) {
        this.transferEventSupport.removeTransferListener(transferListener);
    }

    @Override
    public final boolean hasTransferListener(TransferListener transferListener) {
        return this.transferEventSupport.hasTransferListener(transferListener);
    }

    @Override
    public final boolean isInteractive() {
        return false;
    }

    @Override
    public final void setInteractive(boolean b) {

    }

    @Override
    public final void setTimeout(int i) {

    }

    @Override
    public final int getTimeout() {
        return 0;
    }

    @Override
    public final void setReadTimeout(int i) {

    }

    @Override
    public final int getReadTimeout() {
        return 0;
    }

    private SessionEvent sessionEvent(int e) {
        return new SessionEvent(this, e);
    }

    private void throwNotImplemented(String s) {
        throw new GradleException("This wagon does not yet support the method:" + s);
    }

    private TransferEvent transferEvent(Resource resource, int eventType, int requestType) {
        TransferEvent transferEvent = new TransferEvent(this, resource, eventType, requestType);
        transferEvent.setTimestamp(new Date().getTime());
        return transferEvent;
    }

    private TransferEvent transferEvent(Resource resource, Exception e, int requestType) {
        return new TransferEvent(this, resource, e, requestType);
    }

    private class MavenTransferLoggingFileResource extends FileLocalResource {
        private final Resource resource;

        private MavenTransferLoggingFileResource(File file, Resource resource) {
            super(file);
            this.resource = resource;
        }

        @Override
        public InputStream open() {
            // Need to do this here, so that the transfer is 'restarted' when HttpClient reopens the resource (DIGEST AUTH only)
            transferEventSupport.fireTransferStarted(transferEvent(resource, TRANSFER_STARTED, REQUEST_PUT));
            return new ObservingInputStream(super.open(), resource);
        }

        protected class ObservingInputStream extends InputStream {
            private final InputStream inputStream;
            private final TransferEvent transferEvent;
            private final byte[] singleByteBuffer = new byte[1];

            public ObservingInputStream(InputStream inputStream, Resource resource) {
                this.inputStream = inputStream;
                this.transferEvent = transferEvent(resource, TransferEvent.TRANSFER_PROGRESS, REQUEST_PUT);
            }

            @Override
            public void close() throws IOException {
                inputStream.close();
            }

            @Override
            public int read() throws IOException {
                int result = inputStream.read();
                if (result >= 0) {
                    singleByteBuffer[0] = (byte) result;
                    logTransfer(singleByteBuffer, 1);
                }
                return result;
            }

            public int read(byte[] b, int off, int len) throws IOException {
                int read = inputStream.read(b, off, len);
                if (read > 0) {
                    logTransfer(b, read);
                }
                return read;
            }

            private void logTransfer(byte[] bytes, int read) {
                transferEventSupport.fireTransferProgress(transferEvent, bytes, read);
            }
        }
    }
}
