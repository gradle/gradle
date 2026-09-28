/*
 * Copyright 2026 the original author or authors.
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

/*
 * LD_PRELOAD shim that makes Maven Central and the Gradle Plugin Portal unresolvable, so every
 * process of a CI build that bypasses the repository mirrors fails fast. All other names are
 * resolved by the real libc functions.
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <netdb.h>
#include <stddef.h>
#include <string.h>
#include <strings.h>

static const char *const BLOCKED_HOSTS[] = {
    "repo.maven.apache.org",
    "repo1.maven.org",
    "plugins.gradle.org",
};

static int is_blocked(const char *name) {
    if (name == NULL) {
        return 0;
    }
    size_t length = strlen(name);
    if (length > 0 && name[length - 1] == '.') {
        length--;
    }
    for (size_t i = 0; i < sizeof(BLOCKED_HOSTS) / sizeof(BLOCKED_HOSTS[0]); i++) {
        if (strlen(BLOCKED_HOSTS[i]) == length && strncasecmp(name, BLOCKED_HOSTS[i], length) == 0) {
            return 1;
        }
    }
    return 0;
}

int getaddrinfo(const char *node, const char *service, const struct addrinfo *hints, struct addrinfo **res) {
    static int (*real)(const char *, const char *, const struct addrinfo *, struct addrinfo **);
    if (is_blocked(node)) {
        return EAI_NONAME;
    }
    if (real == NULL) {
        real = dlsym(RTLD_NEXT, "getaddrinfo");
    }
    return real(node, service, hints, res);
}

struct hostent *gethostbyname(const char *name) {
    static struct hostent *(*real)(const char *);
    if (is_blocked(name)) {
        h_errno = HOST_NOT_FOUND;
        return NULL;
    }
    if (real == NULL) {
        real = dlsym(RTLD_NEXT, "gethostbyname");
    }
    return real(name);
}

struct hostent *gethostbyname2(const char *name, int af) {
    static struct hostent *(*real)(const char *, int);
    if (is_blocked(name)) {
        h_errno = HOST_NOT_FOUND;
        return NULL;
    }
    if (real == NULL) {
        real = dlsym(RTLD_NEXT, "gethostbyname2");
    }
    return real(name, af);
}

int gethostbyname_r(const char *name, struct hostent *ret, char *buf, size_t buflen, struct hostent **result, int *h_errnop) {
    static int (*real)(const char *, struct hostent *, char *, size_t, struct hostent **, int *);
    if (is_blocked(name)) {
        *result = NULL;
        *h_errnop = HOST_NOT_FOUND;
        return ENOENT;
    }
    if (real == NULL) {
        real = dlsym(RTLD_NEXT, "gethostbyname_r");
    }
    return real(name, ret, buf, buflen, result, h_errnop);
}

int gethostbyname2_r(const char *name, int af, struct hostent *ret, char *buf, size_t buflen, struct hostent **result, int *h_errnop) {
    static int (*real)(const char *, int, struct hostent *, char *, size_t, struct hostent **, int *);
    if (is_blocked(name)) {
        *result = NULL;
        *h_errnop = HOST_NOT_FOUND;
        return ENOENT;
    }
    if (real == NULL) {
        real = dlsym(RTLD_NEXT, "gethostbyname2_r");
    }
    return real(name, af, ret, buf, buflen, result, h_errnop);
}
