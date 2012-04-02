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
package org.gradle.integtests.fixtures

import java.security.Principal
import java.util.zip.GZIPOutputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.gradle.util.hash.HashUtil
import org.junit.rules.ExternalResource
import org.mortbay.jetty.handler.AbstractHandler
import org.mortbay.jetty.handler.HandlerCollection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.mortbay.jetty.*
import org.mortbay.jetty.security.*

class HttpServer extends ExternalResource {

    private static Logger logger = LoggerFactory.getLogger(HttpServer.class)

    private final Server server = new Server(0)
    private final HandlerCollection collection = new HandlerCollection()
    private Throwable failure
    private TestUserRealm realm
    
    enum EtagStrategy {
        NONE({ null }),
        RAW_SHA1_HEX({ HashUtil.sha1(it as byte[]).asHexString() }),
        NEXUS_ENCODED_SHA1({ "{SHA1{" + HashUtil.sha1(it as byte[]).asHexString() + "}}" })
        
        private final Closure generator
        
        EtagStrategy(Closure generator) {
            this.generator = generator
        }
        
        String generate(byte[] bytes) {
            generator.call(bytes)                        
        }
    }

    // Can be an EtagStrategy, or a closure that receives a byte[] and returns an etag string, or anything that gets toString'd
    def etags = EtagStrategy.NONE

    boolean sendLastModified = true
    boolean sendSha1Header = false

    HttpServer() {
        HandlerCollection handlers = new HandlerCollection()
        handlers.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                println("handling http request: $request.method $target")
            }
        })
        handlers.addHandler(collection)
        handlers.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.handled) {
                    return
                }
                failure = new AssertionError("Received unexpected ${request.method} request to ${target}.")
                logger.error(failure.message)
                response.sendError(404, "'$target' does not exist")
            }
        })
        server.setHandler(handlers)
    }

    void start() {
        server.start()
    }

    void stop() {
        server?.stop()
    }

    void resetExpectations() {
        if (failure != null) {
            throw failure
        }
        collection.setHandlers()
    }

    @Override
    protected void after() {
        stop()
    }

    /**
     * Adds a given file at the given URL. The source file can be either a file or a directory.
     */
    void allowGet(String path, File srcFile) {
        allow(path, true, ['GET', 'HEAD'], fileHandler(path, srcFile))
    }

    private AbstractHandler fileHandler(String path, File srcFile, Long lastModified = null, Long contentLength = null) {
        return new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                def file
                if (request.pathInfo == path) {
                    file = srcFile
                } else {
                    def relativePath = request.pathInfo.substring(path.length() + 1)
                    file = new File(srcFile, relativePath)
                }
                if (file.isFile()) {
                    sendFile(response, file, lastModified, contentLength)
                } else if (file.isDirectory()) {
                    sendDirectoryListing(response, file)
                } else {
                    response.sendError(404, "'$target' does not exist")
                }
            }
        }
    }

    /**
     * Adds a broken resource at the given URL.
     */
    void addBroken(String path) {
        allow(path, true, null, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.sendError(500, "broken")
            }
        })
    }

    /**
     * Allows one GET request for the given URL, which return 404 status code
     */
    void expectGetMissing(String path) {
        expect(path, false, ['GET'], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.sendError(404, "not found")
            }
        })
    }

    /**
     * Allows one HEAD request for the given URL, which return 404 status code
     */
    void expectHeadMissing(String path) {
        expect(path, false, ['HEAD'], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.sendError(404, "not found")
            }
        })
    }

    /**
     * Allows one HEAD request for the given URL.
     */
    void expectHead(String path, File srcFile, Long lastModified = null, Long contentLength = null) {
        expect(path, false, ['HEAD'], fileHandler(path, srcFile, lastModified, contentLength))
    }

    /**
     * Allows one GET request for the given URL. Reads the request content from the given file.
     */
    void expectGet(String path, File srcFile, Long lastModified = null, Long contentLength = null) {
        expect(path, false, ['GET'], fileHandler(path, srcFile, lastModified, contentLength))
    }

    /**
     * Allows one HEAD request, then one GET request for the given URL. Reads the request content from the given file.
     */
    void expectHeadThenGet(String path, File srcFile, Long lastModified = null, Long contentLength = null) {
        expectHead(path, srcFile, lastModified, contentLength)
        expectGet(path, srcFile, lastModified, contentLength)
    }

    /**
     * Allows one GET request for the given URL, with the given credentials. Reads the request content from the given file.
     */
    void expectGet(String path, String username, String password, File srcFile) {
        expect(path, false, ['GET'], withAuthentication(path, username, password, fileHandler(path, srcFile)))
    }

    /**
     * Allows one GET request for the given URL, with the response being GZip encoded.
     */
    void expectGetGZipped(String path, File srcFile) {
        expect(path, false, ['GET'], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                def file = srcFile
                if (file.isFile()) {
                    response.setHeader("Content-Encoding", "gzip")
                    response.setDateHeader(HttpHeaders.LAST_MODIFIED, srcFile.lastModified())
                    def stream = new GZIPOutputStream(response.outputStream)
                    stream.write(file.bytes)
                    stream.close()
                } else {
                    response.sendError(404, "'$target' does not exist")
                }
            }
        });
    }

    /**
     * Allow one GET request for the given URL, responding with a redirect.
     */
    void expectGetRedirected(String path, String location) {
        expectRedirected('GET', path, location)
    }

    /**
     * Allow one HEAD request for the given URL, responding with a redirect.
     */
    void expectHeadRedirected(String path, String location) {
        expectRedirected('HEAD', path, location)
    }

    private void expectRedirected(String method, String path, String location) {
        expect(path, false, [method], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.sendRedirect(location)
            }
        })
    }

    /**
     * Allows one GET request for the given URL, returning an apache-compatible directory listing with the given File names.
     */
    void expectGetDirectoryListing(String path, File directory) {
        expect(path, false, ['GET'], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                sendDirectoryListing(response, directory)
            }
        })
    }

    private sendFile(HttpServletResponse response, File file, Long lastModified, Long contentLength) {
        if (sendLastModified) {
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified ?: file.lastModified())
        }
        response.setContentLength((contentLength ?: file.length()) as int)
        response.setContentType(new MimeTypes().getMimeByExtension(file.name).toString())
        if (sendSha1Header) {
            response.addHeader("X-Checksum-Sha1", HashUtil.sha1(file).asHexString())
        }
        addEtag(response, file.bytes, etags)
        response.outputStream << new FileInputStream(file)
    }

    private addEtag(HttpServletResponse response, byte[] bytes, etagStrategy) {
        if (etagStrategy != null) {
            String value
            if (etags instanceof EtagStrategy) {
                value = etags.generate(bytes)
            } else if (etagStrategy instanceof Closure) {
                value = etagStrategy.call(bytes)
            } else {
                value = etagStrategy.toString()
            }

            if (value != null) {
                response.addHeader(HttpHeaders.ETAG, value)
            }
        }
    }

    private sendDirectoryListing(HttpServletResponse response, File directory) {
        def directoryListing = ""
        for (String fileName: directory.list()) {
            directoryListing += "<a href=\"$fileName\">$fileName</a>"
        }

        response.setContentLength(directoryListing.length())
        response.setContentType("text/plain")
        response.outputStream.bytes = directoryListing.bytes
    }

    /**
     * Allows one PUT request for the given URL. Writes the request content to the given file.
     */
    void expectPut(String path, File destFile, int statusCode = HttpStatus.ORDINAL_200_OK) {
        expect(path, false, ['PUT'], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                destFile.bytes = request.inputStream.bytes
                response.setStatus(statusCode)
            }
        })
    }

    /**
     * Allows one PUT request for the given URL, with the given credentials. Writes the request content to the given file.
     */
    void expectPut(String path, String username, String password, File destFile) {
        expect(path, false, ['PUT'], withAuthentication(path, username, password, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.remoteUser != username) {
                    response.sendError(500, "unexpected username '${request.remoteUser}'")
                    return
                }
                destFile.bytes = request.inputStream.bytes
            }
        }))
    }

    private Handler withAuthentication(String path, String username, String password, Handler handler) {
        if (realm != null) {
            assert realm.username == username
            assert realm.password == password
        } else {
            realm = new TestUserRealm()
            realm.username = username
            realm.password = password
            def constraint = new Constraint()
            constraint.name = Constraint.__BASIC_AUTH
            constraint.authenticate = true
            constraint.roles = ['*'] as String[]
            def constraintMapping = new ConstraintMapping()
            constraintMapping.pathSpec = path
            constraintMapping.constraint = constraint
            def securityHandler = new SecurityHandler()
            securityHandler.userRealm = realm
            securityHandler.constraintMappings = [constraintMapping] as ConstraintMapping[]
            securityHandler.authenticator = new BasicAuthenticator()
            collection.addHandler(securityHandler)
        }

        return new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.remoteUser != username) {
                    response.sendError(500, "unexpected username '${request.remoteUser}'")
                    return
                }
                handler.handle(target, request, response, dispatch)
            }
        }
    }

    private void expect(String path, boolean recursive, Collection<String> methods, Handler handler) {
        boolean run
        add(path, recursive, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (run) {
                    return
                }
                run = true
                handler.handle(target, request, response, dispatch)
                request.handled = true
            }
        })
    }

    private void allow(String path, boolean recursive, Collection<String> methods, Handler handler) {
        add(path, recursive, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                handler.handle(target, request, response, dispatch)
                request.handled = true
            }
        })
    }

    private void add(String path, boolean recursive, Collection<String> methods, Handler handler) {
        assert path.startsWith('/')
//        assert path == '/' || !path.endsWith('/')
        def prefix = path == '/' ? '/' : path + '/'
        collection.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (methods != null && !methods.contains(request.method)) {
                    return
                }
                boolean match = request.pathInfo == path || (recursive && request.pathInfo.startsWith(prefix))
                if (match && !request.handled) {
                    handler.handle(target, request, response, dispatch)
                }
            }
        })
    }

    int getPort() {
        return server.connectors[0].localPort
    }

    static class TestUserRealm implements UserRealm {
        String username
        String password

        Principal authenticate(String username, Object credentials, Request request) {
            if (username == this.username && password == credentials) {
                return getPrincipal(username)
            }
            return null
        }

        String getName() {
            return "test"
        }

        Principal getPrincipal(String username) {
            return new Principal() {
                String getName() {
                    return username
                }
            }
        }

        boolean reauthenticate(Principal user) {
            return false
        }

        boolean isUserInRole(Principal user, String role) {
            return false
        }

        void disassociate(Principal user) {
        }

        Principal pushRole(Principal user, String role) {
            return user
        }

        Principal popRole(Principal user) {
            return user
        }

        void logout(Principal user) {
        }

    }
}
