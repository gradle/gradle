/* assuan.h - Definitions for the Assuan IPC library             -*- c -*-
 * Copyright (C) 2001-2013 Free Software Foundation, Inc.
 * Copyright (C) 2001-2021 g10 Code GmbH
 *
 * This file is part of Assuan.
 *
 * Assuan is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * Assuan is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, see <http://www.gnu.org/licenses/>.
 * SPDX-License-Identifier: LGPL-2.1-or-later
 *
 * Do not edit.  Generated from assuan.h.in by mkheader for mingw32.
 */

/* Compile time configuration:
 *
 * #define _ASSUAN_NO_SOCKET_WRAPPER
 *
 * Do not include the definitions for the socket wrapper feature.
 */

#ifndef ASSUAN_H
#define ASSUAN_H

#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>
#include <stdarg.h>

#ifndef _ASSUAN_NO_SOCKET_WRAPPER
#include <winsock2.h>
#include <ws2tcpip.h>
#endif /*!_ASSUAN_NO_SOCKET_WRAPPER*/

typedef void *assuan_msghdr_t;

#ifdef _MSC_VER
  typedef long ssize_t;
  typedef int  pid_t;
#endif


#include <gpg-error.h>

#ifdef __cplusplus
extern "C"
{
#if 0
}
#endif
#endif

/* The version of this header should match the one of the library.  Do
 * not use this symbol in your application; use assuan_check_version
 * instead.  */
#define ASSUAN_VERSION "2.5.7"

/* The version number of this header.  It may be used to handle minor
 * API incompatibilities.  */
#define ASSUAN_VERSION_NUMBER 0x020507


/* Check for compiler features.  */
#if __GNUC__
#define _ASSUAN_GCC_VERSION (__GNUC__ * 10000 \
                            + __GNUC_MINOR__ * 100 \
                            + __GNUC_PATCHLEVEL__)

#if _ASSUAN_GCC_VERSION > 30100
#define _ASSUAN_DEPRECATED  __attribute__ ((__deprecated__))
#endif
#endif
#ifndef _ASSUAN_DEPRECATED
#define _ASSUAN_DEPRECATED
#endif


#define ASSUAN_LINELENGTH 1002 /* 1000 + [CR,]LF */

struct assuan_context_s;
typedef struct assuan_context_s *assuan_context_t;

/* Because we use system handles and not libc low level file
   descriptors on W32, we need to declare them as HANDLE (which
   actually is a plain pointer).  This is required to eventually
   support 64 bit Windows systems.  */
typedef void *assuan_fd_t;
#define ASSUAN_INVALID_FD ((void*)(-1))
#define ASSUAN_INVALID_PID ((pid_t) -1)
#if GPGRT_HAVE_PRAGMA_GCC_PUSH
# pragma GCC push_options
# pragma GCC diagnostic ignored "-Wbad-function-cast"
#endif
static GPG_ERR_INLINE assuan_fd_t
assuan_fd_from_posix_fd (int fd)
{
  if (fd < 0)
    return ASSUAN_INVALID_FD;
  else
    return (assuan_fd_t) _get_osfhandle (fd);
}
#if GPGRT_HAVE_PRAGMA_GCC_PUSH
# pragma GCC pop_options
#endif


assuan_fd_t assuan_fdopen (int fd);


/* Assuan features an emulation of Unix domain sockets based on local
   TCP connections.  To implement access permissions based on file
   permissions a nonce is used which is expected by the server as the
   first bytes received.  This structure is used by the server to save
   the nonce created initially by bind.  */
struct assuan_sock_nonce_s
{
  size_t length;
  char nonce[16];
};
typedef struct assuan_sock_nonce_s assuan_sock_nonce_t;

/* Define the Unix domain socket structure for Windows.  */
#ifndef _ASSUAN_NO_SOCKET_WRAPPER
# ifndef AF_LOCAL
#  define AF_LOCAL AF_UNIX
# endif
# ifndef EADDRINUSE
#  define EADDRINUSE WSAEADDRINUSE
# endif
struct sockaddr_un
{
  short          sun_family;
  unsigned short sun_port;
  struct         in_addr sun_addr;
  char           sun_path[108-2-4];
};
#endif



/*
 * Global interface.
 */

struct assuan_malloc_hooks
{
  void *(*malloc) (size_t cnt);
  void *(*realloc) (void *ptr, size_t cnt);
  void (*free) (void *ptr);
};
typedef struct assuan_malloc_hooks *assuan_malloc_hooks_t;

/* Categories for log messages.  */
#define ASSUAN_LOG_INIT 1
#define ASSUAN_LOG_CTX 2
#define ASSUAN_LOG_ENGINE 3
#define ASSUAN_LOG_DATA 4
#define ASSUAN_LOG_SYSIO 5
#define ASSUAN_LOG_CONTROL 8

/* If MSG is NULL, return true/false depending on if this category is
 * logged.  This is used to probe before expensive log message
 * generation (buffer dumps).  */
typedef int (*assuan_log_cb_t) (assuan_context_t ctx, void *hook,
				unsigned int cat, const char *msg);

/* Return or check the version number.  */
const char *assuan_check_version (const char *req_version);

/* Set the default gpg error source.  */
void assuan_set_gpg_err_source (gpg_err_source_t errsource);

/* Get the default gpg error source.  */
gpg_err_source_t assuan_get_gpg_err_source (void);


/* Set the default malloc hooks.  */
void assuan_set_malloc_hooks (assuan_malloc_hooks_t malloc_hooks);

/* Get the default malloc hooks.  */
assuan_malloc_hooks_t assuan_get_malloc_hooks (void);


/* Set the default log callback handler.  */
void assuan_set_log_cb (assuan_log_cb_t log_cb, void *log_cb_data);

/* Get the default log callback handler.  */
void assuan_get_log_cb (assuan_log_cb_t *log_cb, void **log_cb_data);


/* Create a new Assuan context.  The initial parameters are all needed
 * in the creation of the context.  */
gpg_error_t assuan_new_ext (assuan_context_t *ctx, gpg_err_source_t errsource,
			    assuan_malloc_hooks_t malloc_hooks,
			    assuan_log_cb_t log_cb, void *log_cb_data);

/* Create a new context with default arguments.  */
gpg_error_t assuan_new (assuan_context_t *ctx);

/* Release all resources associated with the given context.  */
void assuan_release (assuan_context_t ctx);

/* Release the memory at PTR using the allocation handler of the
 * context CTX.  This is a convenience function.  */
void assuan_free (assuan_context_t ctx, void *ptr);


/* Set user-data in a context.  */
void assuan_set_pointer (assuan_context_t ctx, void *pointer);

/* Get user-data in a context.  */
void *assuan_get_pointer (assuan_context_t ctx);


/* Definitions of flags for assuan_set_flag().  */
typedef unsigned int assuan_flag_t;

/* When using a pipe server, by default Assuan will wait for the
 * forked process to die in assuan_release.  In certain cases this
 * is not desirable.  By setting this flag, the waitpid will be
 * skipped and the caller is responsible to cleanup a forked
 * process. */
#define ASSUAN_NO_WAITPID 1

/* This flag indicates whether Assuan logging is in confidential mode.
   You can use assuan_{begin,end}_condidential to change the mode.  */
#define ASSUAN_CONFIDENTIAL 2

/* This flag suppresses fix up of signal handlers for pipes.  */
#define ASSUAN_NO_FIXSIGNALS 3

/* This flag changes assuan_transact to return comment lines via the
 * status callback.  The default is to skip comment lines.  */
#define ASSUAN_CONVEY_COMMENTS 4

/* This flag disables logging for one context.  */
#define ASSUAN_NO_LOGGING 5

/* This flag forces a connection close.  */
#define ASSUAN_FORCE_CLOSE 6


/* For context CTX, set the flag FLAG to VALUE.  Values for flags
 * are usually 1 or 0 but certain flags might allow for other values;
 * see the description of the type assuan_flag_t for details.  */
void assuan_set_flag (assuan_context_t ctx, assuan_flag_t flag, int value);

/* Return the VALUE of FLAG in context CTX.  */
int assuan_get_flag (assuan_context_t ctx, assuan_flag_t flag);

/* Same as assuan_set_flag (ctx, ASSUAN_CONFIDENTIAL, 1).  */
void assuan_begin_confidential (assuan_context_t ctx);

/* Same as assuan_set_flag (ctx, ASSUAN_CONFIDENTIAL, 0).  */
void assuan_end_confidential (assuan_context_t ctx);


/* Direction values for assuan_set_io_monitor.  */
#define ASSUAN_IO_FROM_PEER 0
#define ASSUAN_IO_TO_PEER 1

/* Return flags of I/O monitor.  */
#define ASSUAN_IO_MONITOR_NOLOG 1
#define ASSUAN_IO_MONITOR_IGNORE 2

/* The IO monitor gets to see all I/O on the context, and can return
 * ASSUAN_IO_MONITOR_* bits to control actions on it.  */
typedef unsigned int (*assuan_io_monitor_t) (assuan_context_t ctx, void *hook,
					     int inout, const char *line,
					     size_t linelen);

/* Set the IO monitor function.  */
void assuan_set_io_monitor (assuan_context_t ctx,
			    assuan_io_monitor_t io_monitor, void *hook_data);

/* The system hooks.  See assuan_set_system_hooks et al. */
#define ASSUAN_SYSTEM_HOOKS_VERSION 2
#define ASSUAN_SPAWN_DETACHED 128
struct assuan_system_hooks
{
  /* Always set to ASSUAN_SYTEM_HOOKS_VERSION.  */
  int version;

  /* Sleep for the given number of microseconds.  */
  void (*usleep) (assuan_context_t ctx, unsigned int usec);

  /* Create a pipe with an inheritable end.  */
  int (*pipe) (assuan_context_t ctx, assuan_fd_t fd[2], int inherit_idx);

 /* Close the given file descriptor, created with _assuan_pipe or one
   of the socket functions.  */
  int (*close) (assuan_context_t ctx, assuan_fd_t fd);


  ssize_t (*read) (assuan_context_t ctx, assuan_fd_t fd, void *buffer,
		   size_t size);
  ssize_t (*write) (assuan_context_t ctx, assuan_fd_t fd,
		    const void *buffer, size_t size);

  int (*recvmsg) (assuan_context_t ctx, assuan_fd_t fd, assuan_msghdr_t msg,
		  int flags);
  int (*sendmsg) (assuan_context_t ctx, assuan_fd_t fd,
		  const assuan_msghdr_t msg, int flags);

  /* If NAME is NULL, don't exec, just fork.  FD_CHILD_LIST is
     modified to reflect the value of the FD in the peer process (on
     Windows).  */
  int (*spawn) (assuan_context_t ctx, pid_t *r_pid, const char *name,
		const char **argv,
		assuan_fd_t fd_in, assuan_fd_t fd_out,
		assuan_fd_t *fd_child_list,
		void (*atfork) (void *opaque, int reserved),
		void *atforkvalue, unsigned int flags);

  /* If action is 0, like waitpid.  If action is 1, just release the PID?  */
  pid_t (*waitpid) (assuan_context_t ctx, pid_t pid,
		    int action, int *status, int options);
  int (*socketpair) (assuan_context_t ctx, int _namespace, int style,
		     int protocol, assuan_fd_t filedes[2]);
  int (*socket) (assuan_context_t ctx, int _namespace, int style, int protocol);
  int (*connect) (assuan_context_t ctx, int sock, struct sockaddr *addr, socklen_t length);
};
typedef struct assuan_system_hooks *assuan_system_hooks_t;



/*
 * Configuration of the default log handler.
 */

/* Set the prefix to be used at the start of a line emitted by assuan
 * on the log stream.  The default is the empty string.  Note, that
 * this function is not thread-safe and should in general be used
 * right at startup. */
void assuan_set_assuan_log_prefix (const char *text);

/* Return a prefix to be used at the start of a line emitted by assuan
 * on the log stream.  The default implementation returns the empty
 * string, i.e. "".  */
const char *assuan_get_assuan_log_prefix (void);

/* Global default log stream.  */
void assuan_set_assuan_log_stream (FILE *fp);

/* Set the per context log stream for the default log handler.  */
void assuan_set_log_stream (assuan_context_t ctx, FILE *fp);


/* The type for assuan command handlers.  */
typedef gpg_error_t (*assuan_handler_t) (assuan_context_t, char *);

/*-- assuan-handler.c --*/
gpg_error_t assuan_register_command (assuan_context_t ctx,
				     const char *cmd_string,
				     assuan_handler_t handler,
                                     const char *help_string);
gpg_error_t assuan_register_pre_cmd_notify (assuan_context_t ctx,
                                          gpg_error_t (*fnc)(assuan_context_t,
                                                             const char *cmd));

gpg_error_t assuan_register_post_cmd_notify (assuan_context_t ctx,
					     void (*fnc)(assuan_context_t,
                                                         gpg_error_t));
gpg_error_t assuan_register_bye_notify (assuan_context_t ctx,
					assuan_handler_t handler);
gpg_error_t assuan_register_reset_notify (assuan_context_t ctx,
					  assuan_handler_t handler);
gpg_error_t assuan_register_cancel_notify (assuan_context_t ctx,
					   assuan_handler_t handler);
gpg_error_t assuan_register_input_notify (assuan_context_t ctx,
					  assuan_handler_t handler);
gpg_error_t assuan_register_output_notify (assuan_context_t ctx,
					   assuan_handler_t handler);

gpg_error_t assuan_register_option_handler (assuan_context_t ctx,
					    gpg_error_t (*fnc)(assuan_context_t,
							       const char*,
                                                               const char*));

gpg_error_t assuan_process (assuan_context_t ctx);
gpg_error_t assuan_process_next (assuan_context_t ctx, int *done);
gpg_error_t assuan_process_done (assuan_context_t ctx, gpg_error_t rc);
int assuan_get_active_fds (assuan_context_t ctx, int what,
                           assuan_fd_t *fdarray, int fdarraysize);

const char *assuan_get_command_name (assuan_context_t ctx);

FILE *assuan_get_data_fp (assuan_context_t ctx);
gpg_error_t assuan_set_okay_line (assuan_context_t ctx, const char *line);
gpg_error_t assuan_write_status (assuan_context_t ctx,
				 const char *keyword, const char *text);

/* Negotiate a file descriptor.  If LINE contains "FD=N", returns N
 * assuming a local file descriptor.  If LINE contains "FD" reads a
 * file descriptor via CTX and stores it in *RDF (the CTX must be
 * capable of passing file descriptors).  Under Windows the returned
 * FD is a libc-type one.  */
gpg_error_t assuan_command_parse_fd (assuan_context_t ctx, char *line,
                                        assuan_fd_t *rfd);


/*-- assuan-listen.c --*/
gpg_error_t assuan_set_hello_line (assuan_context_t ctx, const char *line);
gpg_error_t assuan_accept (assuan_context_t ctx);
assuan_fd_t assuan_get_input_fd (assuan_context_t ctx);
assuan_fd_t assuan_get_output_fd (assuan_context_t ctx);
gpg_error_t assuan_close_input_fd (assuan_context_t ctx);
gpg_error_t assuan_close_output_fd (assuan_context_t ctx);


/*-- assuan-pipe-server.c --*/
gpg_error_t assuan_init_pipe_server (assuan_context_t ctx,
				     assuan_fd_t filedes[2]);

/*-- assuan-socket-server.c --*/
#define ASSUAN_SOCKET_SERVER_FDPASSING 1
#define ASSUAN_SOCKET_SERVER_ACCEPTED 2
gpg_error_t assuan_init_socket_server (assuan_context_t ctx,
				       assuan_fd_t listen_fd,
				       unsigned int flags);
void assuan_set_sock_nonce (assuan_context_t ctx, assuan_sock_nonce_t *nonce);

/*-- assuan-pipe-connect.c --*/
#define ASSUAN_PIPE_CONNECT_FDPASSING 1
#define ASSUAN_PIPE_CONNECT_DETACHED 128
gpg_error_t assuan_pipe_connect (assuan_context_t ctx,
				 const char *name,
				 const char *argv[],
				 assuan_fd_t *fd_child_list,
				 void (*atfork) (void *, int),
				 void *atforkvalue,
				 unsigned int flags);

/*-- assuan-socket-connect.c --*/
#define ASSUAN_SOCKET_CONNECT_FDPASSING 1
gpg_error_t assuan_socket_connect (assuan_context_t ctx, const char *name,
				   pid_t server_pid, unsigned int flags);

/*-- assuan-socket-connect.c --*/
gpg_error_t assuan_socket_connect_fd (assuan_context_t ctx, int fd,
				   unsigned int flags);

/*-- context.c --*/
pid_t assuan_get_pid (assuan_context_t ctx);
struct _assuan_peercred
{
#ifdef _WIN32
  /* Empty struct not allowed on some compilers, so, put this (not valid).  */
  pid_t pid;
#else
  pid_t pid;
  uid_t uid;
  gid_t gid;
#endif
};
typedef struct _assuan_peercred *assuan_peercred_t;

gpg_error_t assuan_get_peercred (assuan_context_t ctx,
				 assuan_peercred_t *peercred);



/*
 * Client interface.
 */

/* Client response codes.  */
#define ASSUAN_RESPONSE_ERROR 0
#define ASSUAN_RESPONSE_OK 1
#define ASSUAN_RESPONSE_DATA 2
#define ASSUAN_RESPONSE_INQUIRE 3
#define ASSUAN_RESPONSE_STATUS 4
#define ASSUAN_RESPONSE_END 5
#define ASSUAN_RESPONSE_COMMENT 6
typedef int assuan_response_t;

/* This already de-escapes data lines.  */
gpg_error_t assuan_client_read_response (assuan_context_t ctx,
					 char **line, int *linelen);

gpg_error_t assuan_client_parse_response (assuan_context_t ctx,
					  char *line, int linelen,
					  assuan_response_t *response,
					  int *off);

/*-- assuan-client.c --*/
gpg_error_t
assuan_transact (assuan_context_t ctx,
                 const char *command,
                 gpg_error_t (*data_cb)(void *, const void *, size_t),
                 void *data_cb_arg,
                 gpg_error_t (*inquire_cb)(void*, const char *),
                 void *inquire_cb_arg,
                 gpg_error_t (*status_cb)(void*, const char *),
                 void *status_cb_arg);


/*-- assuan-inquire.c --*/
gpg_error_t assuan_inquire (assuan_context_t ctx, const char *keyword,
                               unsigned char **r_buffer, size_t *r_length,
                               size_t maxlen);
gpg_error_t assuan_inquire_ext (assuan_context_t ctx, const char *keyword,
				size_t maxlen,
				gpg_error_t (*cb) (void *cb_data,
						   gpg_error_t rc,
						   unsigned char *buf,
						   size_t buf_len),
				void *cb_data);
/*-- assuan-buffer.c --*/
gpg_error_t assuan_read_line (assuan_context_t ctx,
                              char **line, size_t *linelen);
int assuan_pending_line (assuan_context_t ctx);
gpg_error_t assuan_write_line (assuan_context_t ctx, const char *line);
gpg_error_t assuan_send_data (assuan_context_t ctx,
                              const void *buffer, size_t length);

/* The file descriptor must be pending before assuan_receivefd is
 * called.  This means that assuan_sendfd should be called *before* the
 * trigger is sent (normally via assuan_write_line ("INPUT FD")).  */
gpg_error_t assuan_sendfd (assuan_context_t ctx, assuan_fd_t fd);
gpg_error_t assuan_receivefd (assuan_context_t ctx, assuan_fd_t *fd);


/*-- assuan-util.c --*/
gpg_error_t assuan_set_error (assuan_context_t ctx, gpg_error_t err,
			      const char *text);



/*-- assuan-socket.c --*/

/* This flag is used with assuan_sock_connect_byname to
 * connect via SOCKS.  */
#define ASSUAN_SOCK_SOCKS   1

/* This flag is used with assuan_sock_connect_byname to force a
   connection via Tor even if the socket subsystem has not been
   swicthed into Tor mode.  This flags overrides ASSUAN_SOCK_SOCKS. */
#define ASSUAN_SOCK_TOR     2

/* These are socket wrapper functions to support an emulation of Unix
 * domain sockets on Windows.  */
gpg_error_t assuan_sock_init (void);
void assuan_sock_deinit (void);
int assuan_sock_close (assuan_fd_t fd);
assuan_fd_t assuan_sock_new (int domain, int type, int proto);
int assuan_sock_set_flag (assuan_fd_t sockfd, const char *name, int value);
int assuan_sock_get_flag (assuan_fd_t sockfd, const char *name, int *r_value);
int assuan_sock_connect (assuan_fd_t sockfd,
                         struct sockaddr *addr, int addrlen);
assuan_fd_t assuan_sock_connect_byname (const char *host, unsigned short port,
                                        int reserved,
                                        const char *credentials,
                                        unsigned int flags);
int assuan_sock_bind (assuan_fd_t sockfd, struct sockaddr *addr, int addrlen);
int assuan_sock_set_sockaddr_un (const char *fname, struct sockaddr *addr,
                                 int *r_redirected);
int assuan_sock_get_nonce (struct sockaddr *addr, int addrlen,
                           assuan_sock_nonce_t *nonce);
int assuan_sock_check_nonce (assuan_fd_t fd, assuan_sock_nonce_t *nonce);
void assuan_sock_set_system_hooks (assuan_system_hooks_t system_hooks);


/* Set the default system callbacks.  This is irreversible.  */
void assuan_set_system_hooks (assuan_system_hooks_t system_hooks);

/* Set the per context system callbacks.  This is irreversible.  */
void assuan_ctx_set_system_hooks (assuan_context_t ctx,
				  assuan_system_hooks_t system_hooks);

/* Change the system hooks for the socket interface.
 * This is not thread-safe.  */
void assuan_sock_set_system_hooks (assuan_system_hooks_t system_hooks);

void __assuan_usleep (assuan_context_t ctx, unsigned int usec);
int __assuan_pipe (assuan_context_t ctx, assuan_fd_t fd[2], int inherit_idx);
int __assuan_close (assuan_context_t ctx, assuan_fd_t fd);
int __assuan_spawn (assuan_context_t ctx, pid_t *r_pid, const char *name,
		    const char **argv, assuan_fd_t fd_in, assuan_fd_t fd_out,
		    assuan_fd_t *fd_child_list,
		    void (*atfork) (void *opaque, int reserved),
		    void *atforkvalue, unsigned int flags);
int __assuan_socketpair (assuan_context_t ctx, int _namespace, int style,
			 int protocol, assuan_fd_t filedes[2]);
int __assuan_socket (assuan_context_t ctx, int _namespace, int style, int protocol);
int __assuan_connect (assuan_context_t ctx, int sock, struct sockaddr *addr, socklen_t length);
ssize_t __assuan_read (assuan_context_t ctx, assuan_fd_t fd, void *buffer, size_t size);
ssize_t __assuan_write (assuan_context_t ctx, assuan_fd_t fd, const void *buffer, size_t size);
int __assuan_recvmsg (assuan_context_t ctx, assuan_fd_t fd, assuan_msghdr_t msg, int flags);
int __assuan_sendmsg (assuan_context_t ctx, assuan_fd_t fd, const assuan_msghdr_t msg, int flags);
pid_t __assuan_waitpid (assuan_context_t ctx, pid_t pid, int nowait, int *status, int options);

/* Standard system hooks for the legacy GNU Pth.  */
#define ASSUAN_SYSTEM_PTH_IMPL						\
  static void _assuan_pth_usleep (assuan_context_t ctx, unsigned int usec) \
  { (void) ctx; pth_usleep (usec); }					\
  static ssize_t _assuan_pth_read (assuan_context_t ctx, assuan_fd_t fd, \
				void *buffer, size_t size)		\
  { (void) ctx; return pth_read (fd, buffer, size); }			\
  static ssize_t _assuan_pth_write (assuan_context_t ctx, assuan_fd_t fd, \
				 const void *buffer, size_t size)	\
  { (void) ctx; return pth_write (fd, buffer, size); }			\
  static int _assuan_pth_recvmsg (assuan_context_t ctx, assuan_fd_t fd, \
				  assuan_msghdr_t msg, int flags)	\
  {									\
    (void) ctx;								\
    gpg_err_set_errno (ENOSYS);                                         \
    return -1;								\
  }									\
  static int _assuan_pth_sendmsg (assuan_context_t ctx, assuan_fd_t fd, \
				  const assuan_msghdr_t msg, int flags) \
  {									\
    (void) ctx;								\
    gpg_err_set_errno (ENOSYS);                                         \
    return -1;								\
  }                                                                     \
  static pid_t _assuan_pth_waitpid (assuan_context_t ctx, pid_t pid,     \
				   int nowait, int *status, int options) \
  { (void) ctx;                                                         \
     if (!nowait) return pth_waitpid (pid, status, options);            \
      else return 0; }							\
									\
  struct assuan_system_hooks _assuan_system_pth =			\
    { ASSUAN_SYSTEM_HOOKS_VERSION, _assuan_pth_usleep, __assuan_pipe,	\
      __assuan_close, _assuan_pth_read, _assuan_pth_write,		\
      _assuan_pth_recvmsg, _assuan_pth_sendmsg,				\
      __assuan_spawn, _assuan_pth_waitpid, __assuan_socketpair,		\
      __assuan_socket, __assuan_connect }

extern struct assuan_system_hooks _assuan_system_pth;
#define ASSUAN_SYSTEM_PTH &_assuan_system_pth

/* Standard system hooks for nPth.  */
#define ASSUAN_SYSTEM_NPTH_IMPL						\
  static void _assuan_npth_usleep (assuan_context_t ctx, unsigned int usec) \
  { npth_unprotect();				                        \
    __assuan_usleep (ctx, usec);					\
    npth_protect(); }							\
  static ssize_t _assuan_npth_read (assuan_context_t ctx, assuan_fd_t fd, \
				    void *buffer, size_t size)		\
  { ssize_t res; (void) ctx; npth_unprotect();				\
    res = __assuan_read (ctx, fd, buffer, size);			\
    npth_protect(); return res; }					\
  static ssize_t _assuan_npth_write (assuan_context_t ctx, assuan_fd_t fd, \
				     const void *buffer, size_t size)	\
  { ssize_t res; (void) ctx; npth_unprotect();				\
    res = __assuan_write (ctx, fd, buffer, size);			\
    npth_protect(); return res; }					\
  static int _assuan_npth_recvmsg (assuan_context_t ctx, assuan_fd_t fd, \
				  assuan_msghdr_t msg, int flags)	\
  { int res; (void) ctx; npth_unprotect();				\
    res = __assuan_recvmsg (ctx, fd, msg, flags);			\
    npth_protect(); return res; }					\
  static int _assuan_npth_sendmsg (assuan_context_t ctx, assuan_fd_t fd, \
				  const assuan_msghdr_t msg, int flags) \
  { int res; (void) ctx; npth_unprotect();				\
    res = __assuan_sendmsg (ctx, fd, msg, flags);			\
    npth_protect(); return res; }					\
  static pid_t _assuan_npth_waitpid (assuan_context_t ctx, pid_t pid,	\
				     int nowait, int *status, int options) \
  { pid_t res; (void) ctx; npth_unprotect();				\
    res = __assuan_waitpid (ctx, pid, nowait, status, options);		\
    npth_protect(); return res; }					\
  static int _assuan_npth_connect (assuan_context_t ctx, int sock,	\
				   struct sockaddr *addr, socklen_t len)\
  { int res; npth_unprotect();						\
    res = __assuan_connect (ctx, sock, addr, len);			\
    npth_protect(); return res; }					\
  static int _assuan_npth_close (assuan_context_t ctx, assuan_fd_t fd)	\
  { int res; npth_unprotect();						\
    res = __assuan_close (ctx, fd);					\
    npth_protect(); return res; }					\
									\
  struct assuan_system_hooks _assuan_system_npth =			\
    { ASSUAN_SYSTEM_HOOKS_VERSION, _assuan_npth_usleep, __assuan_pipe,	\
      _assuan_npth_close, _assuan_npth_read, _assuan_npth_write,	\
      _assuan_npth_recvmsg, _assuan_npth_sendmsg,			\
      __assuan_spawn, _assuan_npth_waitpid, __assuan_socketpair,	\
      __assuan_socket, _assuan_npth_connect }

extern struct assuan_system_hooks _assuan_system_npth;
#define ASSUAN_SYSTEM_NPTH &_assuan_system_npth


#ifdef __cplusplus
}
#endif
#endif /* ASSUAN_H */
/*
Local Variables:
buffer-read-only: t
End:
*/
