/* gpgme.h - Public interface to GnuPG Made Easy.                   -*- c -*-
 * Copyright (C) 2000 Werner Koch (dd9jn)
 * Copyright (C) 2001-2018 g10 Code GmbH
 *
 * This file is part of GPGME.
 *
 * GPGME is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * GPGME is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, see <https://gnu.org/licenses/>.
 * SPDX-License-Identifier: LGPL-2.1-or-later
 *
 * Generated from gpgme.h.in for i686-w64-mingw32.
 */

#ifndef GPGME_H
#define GPGME_H

/* Include stdio.h for the FILE type definition.  */
#include <stdio.h>
#include <time.h>
#include <gpg-error.h>

#ifdef __cplusplus
extern "C" {
#if 0 /*(Make Emacsen's auto-indent happy.)*/
}
#endif
#endif /* __cplusplus */


/* The version of this header should match the one of the library.  Do
 * not use this symbol in your application, use gpgme_check_version
 * instead.  The purpose of this macro is to let autoconf (using the
 * AM_PATH_GPGME macro) check that this header matches the installed
 * library.  */
#define GPGME_VERSION "1.23.2"

/* The version number of this header.  It may be used to handle minor
 * API incompatibilities.  */
#define GPGME_VERSION_NUMBER 0x011702


/* System specific typedefs.  */

#ifdef _WIN64
# include <stdint.h>
  typedef int64_t gpgme_off_t;
  typedef int64_t gpgme_ssize_t;
#else /* _WIN32 */
  typedef long gpgme_off_t;
  typedef long gpgme_ssize_t;
#endif /* _WIN32 */



/*
 * Check for compiler features.
 */
#ifdef GPGRT_INLINE
# define _GPGME_INLINE GPGRT_INLINE
#elif defined(__GNUC__)
# define _GPGME_INLINE __inline__
#elif defined(__STDC_VERSION__) && __STDC_VERSION__ >= 199901L
# define _GPGME_INLINE inline
#else
# define _GPGME_INLINE
#endif


/* The deprecated macro takes the version number of GPGME which
 * introduced the deprecation as parameter for documentation.  */
#ifdef GPGRT_ATTR_DEPRECATED
# define _GPGME_DEPRECATED(a,b) GPGRT_ATTR_DEPRECATED
#elif defined(__GNUC__)
# define _GPGME_GCC_VERSION (__GNUC__ * 10000 \
                             + __GNUC_MINOR__ * 100 \
                             + __GNUC_PATCHLEVEL__)

# if _GPGME_GCC_VERSION > 30100
#  define _GPGME_DEPRECATED(a,b)  __attribute__ ((__deprecated__))
# else
#  define _GPGME_DEPRECATED(a,b)
# endif
#else
# define _GPGME_DEPRECATED(a,b)
#endif


/* The macro _GPGME_DEPRECATED_OUTSIDE_GPGME suppresses warnings for
 * fields we must access in GPGME for ABI compatibility.  */
#ifdef _GPGME_IN_GPGME
#define _GPGME_DEPRECATED_OUTSIDE_GPGME(a,b)
#else
#define _GPGME_DEPRECATED_OUTSIDE_GPGME(a,b) _GPGME_DEPRECATED(a,b)
#endif

/* We used to use some symbols which clash with keywords in some
 * languages.  This macro is used to obsolete them.  */
#if defined(__cplusplus) || defined(SWIGPYTHON)
# define _GPGME_OBSOLETE_SOME_SYMBOLS 1
#endif


/* Check for a matching _FILE_OFFSET_BITS definition.  */
#if 0
#ifndef _FILE_OFFSET_BITS
#error GPGME was compiled with _FILE_OFFSET_BITS = 0, please see the section "Largefile support (LFS)" in the GPGME manual.
#else
#if (_FILE_OFFSET_BITS) != (0)
#error GPGME was compiled with a different value for _FILE_OFFSET_BITS, namely 0, please see the section "Largefile support (LFS)" in the GPGME manual.
#endif
#endif
#endif



/*
 * Some opaque data types used by GPGME.
 */

/* The context holds some global state and configuration options, as
 * well as the results of a crypto operation.  */
struct gpgme_context;
typedef struct gpgme_context *gpgme_ctx_t;

/* The data object is used by GPGME to exchange arbitrary data.  */
struct gpgme_data;
typedef struct gpgme_data *gpgme_data_t;



/*
 * Wrappers for the libgpg-error library.  They are generally not
 * needed and the gpg-error versions may be used instead.
 */

typedef gpg_error_t gpgme_error_t;
typedef gpg_err_code_t gpgme_err_code_t;
typedef gpg_err_source_t gpgme_err_source_t;


static _GPGME_INLINE gpgme_error_t
gpgme_err_make (gpgme_err_source_t source, gpgme_err_code_t code)
{
  return gpg_err_make (source, code);
}


/* The user can define GPGME_ERR_SOURCE_DEFAULT before including this
 * file to specify a default source for gpgme_error.  */
#ifndef GPGME_ERR_SOURCE_DEFAULT
#define GPGME_ERR_SOURCE_DEFAULT  GPG_ERR_SOURCE_USER_1
#endif

static _GPGME_INLINE gpgme_error_t
gpgme_error (gpgme_err_code_t code)
{
  return gpgme_err_make (GPGME_ERR_SOURCE_DEFAULT, code);
}


static _GPGME_INLINE gpgme_err_code_t
gpgme_err_code (gpgme_error_t err)
{
  return gpg_err_code (err);
}


static _GPGME_INLINE gpgme_err_source_t
gpgme_err_source (gpgme_error_t err)
{
  return gpg_err_source (err);
}


/* Return a pointer to a string containing a description of the error
 * code in the error value ERR.  This function is not thread safe.  */
const char *gpgme_strerror (gpgme_error_t err);

/* Return the error string for ERR in the user-supplied buffer BUF of
 * size BUFLEN.  This function is, in contrast to gpg_strerror,
 * thread-safe if a thread-safe strerror_r() function is provided by
 * the system.  If the function succeeds, 0 is returned and BUF
 * contains the string describing the error.  If the buffer was not
 * large enough, ERANGE is returned and BUF contains as much of the
 * beginning of the error string as fits into the buffer.  */
int gpgme_strerror_r (gpg_error_t err, char *buf, size_t buflen);

/* Return a pointer to a string containing a description of the error
 * source in the error value ERR.  */
const char *gpgme_strsource (gpgme_error_t err);

/* Retrieve the error code for the system error ERR.  This returns
 * GPG_ERR_UNKNOWN_ERRNO if the system error is not mapped (report
 * this).  */
gpgme_err_code_t gpgme_err_code_from_errno (int err);

/* Retrieve the system error for the error code CODE.  This returns 0
 * if CODE is not a system error code.  */
int gpgme_err_code_to_errno (gpgme_err_code_t code);

/* Retrieve the error code directly from the ERRNO variable.  This
 * returns GPG_ERR_UNKNOWN_ERRNO if the system error is not mapped
 * (report this) and GPG_ERR_MISSING_ERRNO if ERRNO has the value 0. */
gpgme_err_code_t gpgme_err_code_from_syserror (void);

/* Set the ERRNO variable.  This function is the preferred way to set
 * ERRNO due to peculiarities on WindowsCE.  */
void gpgme_err_set_errno (int err);

/* Return an error value with the error source SOURCE and the system
 *  error ERR.  FIXME: Should be inline.  */
gpgme_error_t gpgme_err_make_from_errno (gpgme_err_source_t source, int err);

/* Return an error value with the system error ERR.
 * inline.  */
gpgme_error_t gpgme_error_from_errno (int err);


static _GPGME_INLINE gpgme_error_t
gpgme_error_from_syserror (void)
{
  return gpgme_error (gpgme_err_code_from_syserror ());
}



/*
 * Various constants and types
 */

/* The possible encoding mode of gpgme_data_t objects.  */
typedef enum
  {
    GPGME_DATA_ENCODING_NONE   = 0,	/* Not specified.  */
    GPGME_DATA_ENCODING_BINARY = 1,
    GPGME_DATA_ENCODING_BASE64 = 2,
    GPGME_DATA_ENCODING_ARMOR  = 3,	/* Either PEM or OpenPGP Armor.  */
    GPGME_DATA_ENCODING_URL    = 4,     /* LF delimited URL list.        */
    GPGME_DATA_ENCODING_URLESC = 5,     /* Ditto, but percent escaped.   */
    GPGME_DATA_ENCODING_URL0   = 6,     /* Nul delimited URL list.       */
    GPGME_DATA_ENCODING_MIME   = 7      /* Data is a MIME part.          */
  }
gpgme_data_encoding_t;


/* Known data types.  */
typedef enum
  {
    GPGME_DATA_TYPE_INVALID      = 0,   /* Not detected.  */
    GPGME_DATA_TYPE_UNKNOWN      = 1,
    GPGME_DATA_TYPE_PGP_SIGNED   = 0x10,
    GPGME_DATA_TYPE_PGP_ENCRYPTED= 0x11,
    GPGME_DATA_TYPE_PGP_OTHER    = 0x12,
    GPGME_DATA_TYPE_PGP_KEY      = 0x13,
    GPGME_DATA_TYPE_PGP_SIGNATURE= 0x18, /* Detached signature */
    GPGME_DATA_TYPE_CMS_SIGNED   = 0x20,
    GPGME_DATA_TYPE_CMS_ENCRYPTED= 0x21,
    GPGME_DATA_TYPE_CMS_OTHER    = 0x22,
    GPGME_DATA_TYPE_X509_CERT    = 0x23,
    GPGME_DATA_TYPE_PKCS12       = 0x24,
  }
gpgme_data_type_t;


/* Public key algorithms.  */
typedef enum
  {
    GPGME_PK_RSA   = 1,
    GPGME_PK_RSA_E = 2,
    GPGME_PK_RSA_S = 3,
    GPGME_PK_ELG_E = 16,
    GPGME_PK_DSA   = 17,
    GPGME_PK_ECC   = 18,
    GPGME_PK_ELG   = 20,
    GPGME_PK_ECDSA = 301,
    GPGME_PK_ECDH  = 302,
    GPGME_PK_EDDSA = 303
  }
gpgme_pubkey_algo_t;


/* Hash algorithms (the values match those from libgcrypt).  */
typedef enum
  {
    GPGME_MD_NONE          = 0,
    GPGME_MD_MD5           = 1,
    GPGME_MD_SHA1          = 2,
    GPGME_MD_RMD160        = 3,
    GPGME_MD_MD2           = 5,
    GPGME_MD_TIGER         = 6,   /* TIGER/192. */
    GPGME_MD_HAVAL         = 7,   /* HAVAL, 5 pass, 160 bit. */
    GPGME_MD_SHA256        = 8,
    GPGME_MD_SHA384        = 9,
    GPGME_MD_SHA512        = 10,
    GPGME_MD_SHA224        = 11,
    GPGME_MD_MD4           = 301,
    GPGME_MD_CRC32	   = 302,
    GPGME_MD_CRC32_RFC1510 = 303,
    GPGME_MD_CRC24_RFC2440 = 304
  }
gpgme_hash_algo_t;


/* The available signature mode flags.  */
typedef enum
  {
    GPGME_SIG_MODE_NORMAL = 0,
    GPGME_SIG_MODE_DETACH = 1,
    GPGME_SIG_MODE_CLEAR  = 2,
    GPGME_SIG_MODE_ARCHIVE = 4
  }
gpgme_sig_mode_t;


/* The available validities for a key.  */
typedef enum
  {
    GPGME_VALIDITY_UNKNOWN   = 0,
    GPGME_VALIDITY_UNDEFINED = 1,
    GPGME_VALIDITY_NEVER     = 2,
    GPGME_VALIDITY_MARGINAL  = 3,
    GPGME_VALIDITY_FULL      = 4,
    GPGME_VALIDITY_ULTIMATE  = 5
  }
gpgme_validity_t;


/* The TOFU policies. */
typedef enum
  {
    GPGME_TOFU_POLICY_NONE    = 0,
    GPGME_TOFU_POLICY_AUTO    = 1,
    GPGME_TOFU_POLICY_GOOD    = 2,
    GPGME_TOFU_POLICY_UNKNOWN = 3,
    GPGME_TOFU_POLICY_BAD     = 4,
    GPGME_TOFU_POLICY_ASK     = 5
  }
gpgme_tofu_policy_t;


/* The key origin values. */
typedef enum
  {
    GPGME_KEYORG_UNKNOWN      = 0,
    GPGME_KEYORG_KS           = 1,
    GPGME_KEYORG_DANE         = 3,
    GPGME_KEYORG_WKD          = 4,
    GPGME_KEYORG_URL          = 5,
    GPGME_KEYORG_FILE         = 6,
    GPGME_KEYORG_SELF         = 7,
    GPGME_KEYORG_OTHER        = 31
  }
gpgme_keyorg_t;


/* The available protocols.  */
typedef enum
  {
    GPGME_PROTOCOL_OpenPGP = 0,  /* The default mode.  */
    GPGME_PROTOCOL_CMS     = 1,
    GPGME_PROTOCOL_GPGCONF = 2,  /* Special code for gpgconf.  */
    GPGME_PROTOCOL_ASSUAN  = 3,  /* Low-level access to an Assuan server.  */
    GPGME_PROTOCOL_G13     = 4,
    GPGME_PROTOCOL_UISERVER= 5,
    GPGME_PROTOCOL_SPAWN   = 6,  /* Direct access to any program.  */
    GPGME_PROTOCOL_DEFAULT = 254,
    GPGME_PROTOCOL_UNKNOWN = 255
  }
gpgme_protocol_t;
/* Convenience macro for the surprisingly mixed spelling.  */
#define GPGME_PROTOCOL_OPENPGP GPGME_PROTOCOL_OpenPGP


/* The available keylist mode flags.  */
#define GPGME_KEYLIST_MODE_LOCAL		1
#define GPGME_KEYLIST_MODE_EXTERN		2
#define GPGME_KEYLIST_MODE_SIGS			4
#define GPGME_KEYLIST_MODE_SIG_NOTATIONS	8
#define GPGME_KEYLIST_MODE_WITH_SECRET       	16
#define GPGME_KEYLIST_MODE_WITH_TOFU       	32
#define GPGME_KEYLIST_MODE_WITH_KEYGRIP       	64
#define GPGME_KEYLIST_MODE_EPHEMERAL            128
#define GPGME_KEYLIST_MODE_VALIDATE		256
#define GPGME_KEYLIST_MODE_FORCE_EXTERN		512
#define GPGME_KEYLIST_MODE_WITH_V5FPR		1024

#define GPGME_KEYLIST_MODE_LOCATE		(1|2)
#define GPGME_KEYLIST_MODE_LOCATE_EXTERNAL	(1|2|512)

typedef unsigned int gpgme_keylist_mode_t;


/* The pinentry modes. */
typedef enum
  {
    GPGME_PINENTRY_MODE_DEFAULT  = 0,
    GPGME_PINENTRY_MODE_ASK      = 1,
    GPGME_PINENTRY_MODE_CANCEL   = 2,
    GPGME_PINENTRY_MODE_ERROR    = 3,
    GPGME_PINENTRY_MODE_LOOPBACK = 4
  }
gpgme_pinentry_mode_t;


/* The available export mode flags.  */
#define GPGME_EXPORT_MODE_EXTERN                2
#define GPGME_EXPORT_MODE_MINIMAL               4
#define GPGME_EXPORT_MODE_SECRET               16
#define GPGME_EXPORT_MODE_RAW                  32
#define GPGME_EXPORT_MODE_PKCS12               64
#define GPGME_EXPORT_MODE_SSH                 256
#define GPGME_EXPORT_MODE_SECRET_SUBKEY       512

typedef unsigned int gpgme_export_mode_t;


/* Flags for the audit log functions.  */
#define GPGME_AUDITLOG_DEFAULT   0
#define GPGME_AUDITLOG_HTML      1
#define GPGME_AUDITLOG_DIAG      2
#define GPGME_AUDITLOG_WITH_HELP 128


/* The available signature notation flags.  */
#define GPGME_SIG_NOTATION_HUMAN_READABLE	1
#define GPGME_SIG_NOTATION_CRITICAL		2

typedef unsigned int gpgme_sig_notation_flags_t;

/* An object to hold information about notation data.  This structure
 * shall be considered read-only and an application must not allocate
 * such a structure on its own.  */
struct _gpgme_sig_notation
{
  struct _gpgme_sig_notation *next;

  /* If NAME is a null pointer, then VALUE contains a policy URL
   * rather than a notation.  */
  char *name;

  /* The value of the notation data.  */
  char *value;

  /* The length of the name of the notation data.  */
  int name_len;

  /* The length of the value of the notation data.  */
  int value_len;

  /* The accumulated flags.  */
  gpgme_sig_notation_flags_t flags;

  /* Notation data is human-readable.  */
  unsigned int human_readable : 1;

  /* Notation data is critical.  */
  unsigned int critical : 1;

  /* Internal to GPGME, do not use.  */
  int _unused : 30;
};
typedef struct _gpgme_sig_notation *gpgme_sig_notation_t;



/*
 * Public structures.
 */

/* The engine information structure.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_engine_info
{
  struct _gpgme_engine_info *next;

  /* The protocol ID.  */
  gpgme_protocol_t protocol;

  /* The file name of the engine binary.  */
  char *file_name;

  /* The version string of the installed engine.  */
  char *version;

  /* The minimum version required for GPGME.  */
  const char *req_version;

  /* The home directory used, or NULL if default.  */
  char *home_dir;
};
typedef struct _gpgme_engine_info *gpgme_engine_info_t;


/* An object with TOFU information.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_tofu_info
{
  struct _gpgme_tofu_info *next;

  /* The TOFU validity:
   *  0 := conflict
   *  1 := key without history
   *  2 := key with too little history
   *  3 := key with enough history for basic trust
   *  4 := key with a lot of history
   */
  unsigned int validity : 3;

  /* The TOFU policy (gpgme_tofu_policy_t).  */
  unsigned int policy : 4;

  unsigned int _rfu : 25;

  /* Number of signatures seen for this binding.  Capped at USHRT_MAX.  */
  unsigned short signcount;
  /* Number of encryptions done with this binding.  Capped at USHRT_MAX.  */
  unsigned short encrcount;

  /* Number of seconds since Epoch when the first and the most
   * recently seen message were verified/decrypted.  0 means unknown. */
  unsigned long signfirst;
  unsigned long signlast;
  unsigned long encrfirst;
  unsigned long encrlast;

  /* If non-NULL a human readable string summarizing the TOFU data. */
  char *description;
};
typedef struct _gpgme_tofu_info *gpgme_tofu_info_t;


/* A subkey from a key.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_subkey
{
  struct _gpgme_subkey *next;

  /* True if subkey is revoked.  */
  unsigned int revoked : 1;

  /* True if subkey is expired.  */
  unsigned int expired : 1;

  /* True if subkey is disabled.  */
  unsigned int disabled : 1;

  /* True if subkey is invalid.  */
  unsigned int invalid : 1;

  /* True if subkey can be used for encryption.  */
  unsigned int can_encrypt : 1;

  /* True if subkey can be used for signing.  */
  unsigned int can_sign : 1;

  /* True if subkey can be used for certification.  */
  unsigned int can_certify : 1;

  /* True if subkey is secret.  */
  unsigned int secret : 1;

  /* True if subkey can be used for authentication.  */
  unsigned int can_authenticate : 1;

  /* True if subkey is qualified for signatures according to German law.  */
  unsigned int is_qualified : 1;

  /* True if the secret key is stored on a smart card.  */
  unsigned int is_cardkey : 1;

  /* True if the key is compliant to the de-vs mode.  */
  unsigned int is_de_vs : 1;

  /* True if the key can be used for restricted encryption (ADSK).  */
  unsigned int can_renc : 1;

  /* True if the key can be used for timestamping.  */
  unsigned int can_timestamp : 1;

  /* True if the private key is possessed by more than one person.  */
  unsigned int is_group_owned : 1;

  /* Internal to GPGME, do not use.  */
  unsigned int _unused : 17;

  /* Public key algorithm supported by this subkey.  */
  gpgme_pubkey_algo_t pubkey_algo;

  /* Length of the subkey.  */
  unsigned int length;

  /* The key ID of the subkey.  */
  char *keyid;

  /* Internal to GPGME, do not use.  */
  char _keyid[16 + 1];

  /* The fingerprint of the subkey in hex digit form.  */
  char *fpr;

  /* The creation timestamp, -1 if invalid, 0 if not available.  */
  long int timestamp;

  /* The expiration timestamp, 0 if the subkey does not expire.  */
  long int expires;

  /* The serial number of a smart card holding this key or NULL.  */
  char *card_number;

  /* The name of the curve for ECC algorithms or NULL.  */
  char *curve;

  /* The keygrip of the subkey in hex digit form or NULL if not available.  */
  char *keygrip;

  /* For OpenPGP the v5 fpr of a v4 key. For X.509 the SHA256 fingerprint.  */
  char *v5fpr;
};
typedef struct _gpgme_subkey *gpgme_subkey_t;


/* A signature on a user ID.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_key_sig
{
  struct _gpgme_key_sig *next;

  /* True if the signature is a revocation signature.  */
  unsigned int revoked : 1;

  /* True if the signature is expired.  */
  unsigned int expired : 1;

  /* True if the signature is invalid.  */
  unsigned int invalid : 1;

  /* True if the signature should be exported.  */
  unsigned int exportable : 1;

  /* Internal to GPGME, do not use.  */
  unsigned int _unused : 12;

  /* The depth of a trust signature, 0 if no trust signature.  */
  unsigned int trust_depth : 8;

  /* The trust value of a trust signature, 0 if no trust signature.  */
  unsigned int trust_value : 8;

  /* The public key algorithm used to create the signature.  */
  gpgme_pubkey_algo_t pubkey_algo;

  /* The key ID of key used to create the signature.  */
  char *keyid;

  /* Internal to GPGME, do not use.  */
  char _keyid[16 + 1];

  /* The creation timestamp, -1 if invalid, 0 if not available.  */
  long int timestamp;

  /* The expiration timestamp, 0 if the subkey does not expire.  */
  long int expires;

  /* Same as in gpgme_signature_t.  */
  gpgme_error_t status;

  /* Deprecated; use SIG_CLASS instead.  */
#ifdef _GPGME_OBSOLETE_SOME_SYMBOLS
  unsigned int _obsolete_class _GPGME_DEPRECATED(0,4);
#else
  unsigned int class _GPGME_DEPRECATED_OUTSIDE_GPGME(0,4);
#endif

  /* The user ID string.  */
  char *uid;

  /* The name part of the user ID.  */
  char *name;

  /* The email part of the user ID.  */
  char *email;

  /* The comment part of the user ID.  */
  char *comment;

  /* Crypto backend specific signature class.  */
  unsigned int sig_class;

  /* Notation data and policy URLs.  */
  gpgme_sig_notation_t notations;

  /* Internal to GPGME, do not use.  */
  gpgme_sig_notation_t _last_notation;

  /* The scope of a trust signature.  Might be NULL.  */
  char *trust_scope;
};
typedef struct _gpgme_key_sig *gpgme_key_sig_t;


/* An user ID from a key.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_user_id
{
  struct _gpgme_user_id *next;

  /* True if the user ID is revoked.  */
  unsigned int revoked : 1;

  /* True if the user ID is invalid.  */
  unsigned int invalid : 1;

  /* Internal to GPGME, do not use.  */
  unsigned int _unused : 25;

  /* Origin of this user ID.  */
  unsigned int origin : 5;

  /* The validity of the user ID.  */
  gpgme_validity_t validity;

  /* The user ID string.  */
  char *uid;

  /* The name part of the user ID.  */
  char *name;

  /* The email part of the user ID.  */
  char *email;

  /* The comment part of the user ID.  */
  char *comment;

  /* The signatures of the user ID.  */
  gpgme_key_sig_t signatures;

  /* Internal to GPGME, do not use.  */
  gpgme_key_sig_t _last_keysig;

  /* The mail address (addr-spec from RFC5322) of the UID string.
   * This is general the same as the EMAIL part of this struct but
   * might be slightly different.  If no mail address is available
   * NULL is stored.  */
  char *address;

  /* The malloced TOFU information or NULL.  */
  gpgme_tofu_info_t tofu;

  /* Time of the last refresh of this user id.  0 if unknown.  */
  unsigned long last_update;

  /* The string to exactly identify a userid.  Might be NULL.  */
  char *uidhash;
};
typedef struct _gpgme_user_id *gpgme_user_id_t;


/* A key from the keyring.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_key
{
  /* Internal to GPGME, do not use.  */
  unsigned int _refs;

  /* True if key is revoked.  */
  unsigned int revoked : 1;

  /* True if key is expired.  */
  unsigned int expired : 1;

  /* True if key is disabled.  */
  unsigned int disabled : 1;

  /* True if key is invalid.  */
  unsigned int invalid : 1;

  /* True if key can be used for encryption.  */
  unsigned int can_encrypt : 1;

  /* True if key can be used for signing.  */
  unsigned int can_sign : 1;

  /* True if key can be used for certification.  */
  unsigned int can_certify : 1;

  /* True if key is secret.  */
  unsigned int secret : 1;

  /* True if key can be used for authentication.  */
  unsigned int can_authenticate : 1;

  /* True if subkey is qualified for signatures according to German law.  */
  unsigned int is_qualified : 1;

  /* True if key has at least one encryption subkey.  */
  unsigned int has_encrypt : 1;

  /* True if key has at least one signing subkey.  */
  unsigned int has_sign : 1;

  /* True if key has a certification capability.  */
  unsigned int has_certify : 1;

  /* True if key has at least one authentication subkey.  */
  unsigned int has_authenticate : 1;

  /* Internal to GPGME, do not use.  */
  unsigned int _unused : 13;

  /* Origin of this key.  */
  unsigned int origin : 5;

  /* This is the protocol supported by this key.  */
  gpgme_protocol_t protocol;

  /* If protocol is GPGME_PROTOCOL_CMS, this string contains the
     issuer serial.  */
  char *issuer_serial;

  /* If protocol is GPGME_PROTOCOL_CMS, this string contains the
     issuer name.  */
  char *issuer_name;

  /* If protocol is GPGME_PROTOCOL_CMS, this string contains the chain
     ID.  */
  char *chain_id;

  /* If protocol is GPGME_PROTOCOL_OpenPGP, this field contains the
     owner trust.  */
  gpgme_validity_t owner_trust;

  /* The subkeys of the key.  */
  gpgme_subkey_t subkeys;

  /* The user IDs of the key.  */
  gpgme_user_id_t uids;

  /* Internal to GPGME, do not use.  */
  gpgme_subkey_t _last_subkey;

  /* Internal to GPGME, do not use.  */
  gpgme_user_id_t _last_uid;

  /* The keylist mode that was active when listing the key.  */
  gpgme_keylist_mode_t keylist_mode;

  /* This field gives the fingerprint of the primary key.  Note that
   * this is a copy of the FPR of the first subkey.  We need it here
   * to allow for an incomplete key object.  */
  char *fpr;

  /* Time of the last refresh of the entire key.  0 if unknown.  */
  unsigned long last_update;
};
typedef struct _gpgme_key *gpgme_key_t;


/* An invalid key object.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_invalid_key
{
  struct _gpgme_invalid_key *next;

  /* The string used to request the key.  Despite the name this may
   * not be a fingerprint.  */
  char *fpr;

  /* The error code.  */
  gpgme_error_t reason;
};
typedef struct _gpgme_invalid_key *gpgme_invalid_key_t;



/*
 * Types for callback functions.
 */

/* Request a passphrase from the user.  */
typedef gpgme_error_t (*gpgme_passphrase_cb_t) (void *hook,
						const char *uid_hint,
						const char *passphrase_info,
						int prev_was_bad, int fd);

/* Inform the user about progress made.  */
typedef void (*gpgme_progress_cb_t) (void *opaque, const char *what,
				     int type, int current, int total);

/* Status messages from gpg. */
typedef gpgme_error_t (*gpgme_status_cb_t) (void *opaque, const char *keyword,
                                            const char *args);

/* Interact with the user about an edit operation.  */
typedef gpgme_error_t (*gpgme_interact_cb_t) (void *opaque,
                                              const char *keyword,
                                              const char *args, int fd);



/*
 * Context management functions.
 */

/* Create a new context and return it in CTX.  */
gpgme_error_t gpgme_new (gpgme_ctx_t *ctx);

/* Release the context CTX.  */
void gpgme_release (gpgme_ctx_t ctx);

/* Set the flag NAME for CTX to VALUE.  */
gpgme_error_t gpgme_set_ctx_flag (gpgme_ctx_t ctx,
                                  const char *name, const char *value);

/* Get the value of the flag NAME from CTX.  */
const char *gpgme_get_ctx_flag (gpgme_ctx_t ctx, const char *name);

/* Set the protocol to be used by CTX to PROTO.  */
gpgme_error_t gpgme_set_protocol (gpgme_ctx_t ctx, gpgme_protocol_t proto);

/* Get the protocol used with CTX */
gpgme_protocol_t gpgme_get_protocol (gpgme_ctx_t ctx);

/* Set the crypto protocol to be used by CTX to PROTO.
 * gpgme_set_protocol actually sets the backend engine.  This sets the
 * crypto protocol used in engines that support more than one crypto
 * prococol (for example, an UISERVER can support OpenPGP and CMS).
 * This is reset to the default with gpgme_set_protocol.  */
gpgme_error_t gpgme_set_sub_protocol (gpgme_ctx_t ctx,
				      gpgme_protocol_t proto);

/* Get the sub protocol.  */
gpgme_protocol_t gpgme_get_sub_protocol (gpgme_ctx_t ctx);

/* Get the string describing protocol PROTO, or NULL if invalid.  */
const char *gpgme_get_protocol_name (gpgme_protocol_t proto);

/* If YES is non-zero, enable armor mode in CTX, disable it otherwise.  */
void gpgme_set_armor (gpgme_ctx_t ctx, int yes);

/* Return non-zero if armor mode is set in CTX.  */
int gpgme_get_armor (gpgme_ctx_t ctx);

/* If YES is non-zero, enable text mode in CTX, disable it otherwise.  */
void gpgme_set_textmode (gpgme_ctx_t ctx, int yes);

/* Return non-zero if text mode is set in CTX.  */
int gpgme_get_textmode (gpgme_ctx_t ctx);

/* If YES is non-zero, enable offline mode in CTX, disable it otherwise.  */
void gpgme_set_offline (gpgme_ctx_t ctx, int yes);

/* Return non-zero if offline mode is set in CTX.  */
int gpgme_get_offline (gpgme_ctx_t ctx);

/* Use whatever the default of the backend crypto engine is.  */
#define GPGME_INCLUDE_CERTS_DEFAULT	-256

/* Include up to NR_OF_CERTS certificates in an S/MIME message.  */
void gpgme_set_include_certs (gpgme_ctx_t ctx, int nr_of_certs);

/* Return the number of certs to include in an S/MIME message.  */
int gpgme_get_include_certs (gpgme_ctx_t ctx);

/* Set keylist mode in CTX to MODE.  */
gpgme_error_t gpgme_set_keylist_mode (gpgme_ctx_t ctx,
				      gpgme_keylist_mode_t mode);

/* Get keylist mode in CTX.  */
gpgme_keylist_mode_t gpgme_get_keylist_mode (gpgme_ctx_t ctx);

/* Set the pinentry mode for CTX to MODE. */
gpgme_error_t gpgme_set_pinentry_mode (gpgme_ctx_t ctx,
                                       gpgme_pinentry_mode_t mode);

/* Get the pinentry mode of CTX.  */
gpgme_pinentry_mode_t gpgme_get_pinentry_mode (gpgme_ctx_t ctx);

/* Set the passphrase callback function in CTX to CB.  HOOK_VALUE is
 * passed as first argument to the passphrase callback function.  */
void gpgme_set_passphrase_cb (gpgme_ctx_t ctx,
                              gpgme_passphrase_cb_t cb, void *hook_value);

/* Get the current passphrase callback function in *CB and the current
 * hook value in *HOOK_VALUE.  */
void gpgme_get_passphrase_cb (gpgme_ctx_t ctx, gpgme_passphrase_cb_t *cb,
			      void **hook_value);

/* Set the progress callback function in CTX to CB.  HOOK_VALUE is
 * passed as first argument to the progress callback function.  */
void gpgme_set_progress_cb (gpgme_ctx_t c, gpgme_progress_cb_t cb,
			    void *hook_value);

/* Get the current progress callback function in *CB and the current
 * hook value in *HOOK_VALUE.  */
void gpgme_get_progress_cb (gpgme_ctx_t ctx, gpgme_progress_cb_t *cb,
			    void **hook_value);

/* Set the status callback function in CTX to CB.  HOOK_VALUE is
 * passed as first argument to the status callback function.  */
void gpgme_set_status_cb (gpgme_ctx_t c, gpgme_status_cb_t cb,
                          void *hook_value);

/* Get the current status callback function in *CB and the current
 * hook value in *HOOK_VALUE.  */
void gpgme_get_status_cb (gpgme_ctx_t ctx, gpgme_status_cb_t *cb,
                          void **hook_value);

/* This function sets the locale for the context CTX, or the default
 * locale if CTX is a null pointer.  */
gpgme_error_t gpgme_set_locale (gpgme_ctx_t ctx, int category,
				const char *value);

/* Get the information about the configured engines.  A pointer to the
 * first engine in the statically allocated linked list is returned.
 * The returned data is valid until the next gpgme_ctx_set_engine_info.  */
gpgme_engine_info_t gpgme_ctx_get_engine_info (gpgme_ctx_t ctx);

/* Set the engine info for the context CTX, protocol PROTO, to the
 * file name FILE_NAME and the home directory HOME_DIR.  */
gpgme_error_t gpgme_ctx_set_engine_info (gpgme_ctx_t ctx,
					 gpgme_protocol_t proto,
					 const char *file_name,
					 const char *home_dir);

/* Delete all signers from CTX.  */
void gpgme_signers_clear (gpgme_ctx_t ctx);

/* Add KEY to list of signers in CTX.  */
gpgme_error_t gpgme_signers_add (gpgme_ctx_t ctx, const gpgme_key_t key);

/* Return the number of signers in CTX.  */
unsigned int gpgme_signers_count (const gpgme_ctx_t ctx);

/* Return the SEQth signer's key in CTX.  */
gpgme_key_t gpgme_signers_enum (const gpgme_ctx_t ctx, int seq);

/* Clear all notation data from the context.  */
void gpgme_sig_notation_clear (gpgme_ctx_t ctx);

/* Add the human-readable notation data with name NAME and value VALUE
 * to the context CTX, using the flags FLAGS.  If NAME is NULL, then
 * VALUE should be a policy URL.  The flag
 * GPGME_SIG_NOTATION_HUMAN_READABLE is forced to be true for notation
 * data, and false for policy URLs.  */
gpgme_error_t gpgme_sig_notation_add (gpgme_ctx_t ctx, const char *name,
				      const char *value,
				      gpgme_sig_notation_flags_t flags);

/* Get the sig notations for this context.  */
gpgme_sig_notation_t gpgme_sig_notation_get (gpgme_ctx_t ctx);

/* Store a sender address in the context.  */
gpgme_error_t gpgme_set_sender (gpgme_ctx_t ctx, const char *address);

/* Get the sender address from the context.  */
const char *gpgme_get_sender (gpgme_ctx_t ctx);



/*
 * Run control.
 */

/* The type of an I/O callback function.  */
typedef gpgme_error_t (*gpgme_io_cb_t) (void *data, int fd);

/* The type of a function that can register FNC as the I/O callback
 * function for the file descriptor FD with direction dir (0: for writing,
 * 1: for reading).  FNC_DATA should be passed as DATA to FNC.  The
 * function should return a TAG suitable for the corresponding
 * gpgme_remove_io_cb_t, and an error value.  */
typedef gpgme_error_t (*gpgme_register_io_cb_t) (void *data, int fd, int dir,
						 gpgme_io_cb_t fnc,
						 void *fnc_data, void **tag);

/* The type of a function that can remove a previously registered I/O
 * callback function given TAG as returned by the register
 * function.  */
typedef void (*gpgme_remove_io_cb_t) (void *tag);

typedef enum
  {
    GPGME_EVENT_START,
    GPGME_EVENT_DONE,
    GPGME_EVENT_NEXT_KEY,
    GPGME_EVENT_NEXT_TRUSTITEM  /* NOT USED.  */
  }
gpgme_event_io_t;

struct gpgme_io_event_done_data
{
  /* A fatal IPC error or an operational error in state-less
   * protocols.  */
  gpgme_error_t err;

  /* An operational errors in session-based protocols.  */
  gpgme_error_t op_err;
};
typedef struct gpgme_io_event_done_data *gpgme_io_event_done_data_t;

/* The type of a function that is called when a context finished an
 * operation.  */
typedef void (*gpgme_event_io_cb_t) (void *data, gpgme_event_io_t type,
				     void *type_data);

struct gpgme_io_cbs
{
  gpgme_register_io_cb_t add;
  void *add_priv;
  gpgme_remove_io_cb_t remove;
  gpgme_event_io_cb_t event;
  void *event_priv;
};
typedef struct gpgme_io_cbs *gpgme_io_cbs_t;

/* Set the I/O callback functions in CTX to IO_CBS.  */
void gpgme_set_io_cbs (gpgme_ctx_t ctx, gpgme_io_cbs_t io_cbs);

/* Get the current I/O callback functions.  */
void gpgme_get_io_cbs (gpgme_ctx_t ctx, gpgme_io_cbs_t io_cbs);

/* Wrappers around the internal I/O functions for use with
 * gpgme_passphrase_cb_t and gpgme_interact_cb_t.  */
gpgme_ssize_t gpgme_io_read (int fd, void *buffer, size_t count);
gpgme_ssize_t gpgme_io_write (int fd, const void *buffer, size_t count);
int     gpgme_io_writen (int fd, const void *buffer, size_t count);

/* Process the pending operation and, if HANG is non-zero, wait for
 * the pending operation to finish.  */
gpgme_ctx_t gpgme_wait (gpgme_ctx_t ctx, gpgme_error_t *status, int hang);

gpgme_ctx_t gpgme_wait_ext (gpgme_ctx_t ctx, gpgme_error_t *status,
			    gpgme_error_t *op_err, int hang);

/* Cancel a pending asynchronous operation.  */
gpgme_error_t gpgme_cancel (gpgme_ctx_t ctx);

/* Cancel a pending operation asynchronously.  */
gpgme_error_t gpgme_cancel_async (gpgme_ctx_t ctx);



/*
 * Functions to handle data objects.
 */

/* Read up to SIZE bytes into buffer BUFFER from the data object with
 * the handle HANDLE.  Return the number of characters read, 0 on EOF
 * and -1 on error.  If an error occurs, errno is set.  */
typedef gpgme_ssize_t (*gpgme_data_read_cb_t) (void *handle, void *buffer,
					 size_t size);

/* Write up to SIZE bytes from buffer BUFFER to the data object with
 * the handle HANDLE.  Return the number of characters written, or -1
 * on error.  If an error occurs, errno is set.  */
typedef gpgme_ssize_t (*gpgme_data_write_cb_t) (void *handle, const void *buffer,
					  size_t size);

/* Set the current position from where the next read or write starts
 * in the data object with the handle HANDLE to OFFSET, relativ to
 * WHENCE.  Returns the new offset in bytes from the beginning of the
 * data object.  */
typedef gpgme_off_t (*gpgme_data_seek_cb_t) (void *handle,
                                       gpgme_off_t offset, int whence);

/* Close the data object with the handle HANDLE.  */
typedef void (*gpgme_data_release_cb_t) (void *handle);

struct gpgme_data_cbs
{
  gpgme_data_read_cb_t read;
  gpgme_data_write_cb_t write;
  gpgme_data_seek_cb_t seek;
  gpgme_data_release_cb_t release;
};
typedef struct gpgme_data_cbs *gpgme_data_cbs_t;

/* Read up to SIZE bytes into buffer BUFFER from the data object with
 * the handle DH.  Return the number of characters read, 0 on EOF and
 * -1 on error.  If an error occurs, errno is set.  */
gpgme_ssize_t gpgme_data_read (gpgme_data_t dh, void *buffer, size_t size);

/* Write up to SIZE bytes from buffer BUFFER to the data object with
 * the handle DH.  Return the number of characters written, or -1 on
 * error.  If an error occurs, errno is set.  */
gpgme_ssize_t gpgme_data_write (gpgme_data_t dh, const void *buffer, size_t size);

/* Set the current position from where the next read or write starts
 * in the data object with the handle DH to OFFSET, relativ to WHENCE.
 * Returns the new offset in bytes from the beginning of the data
 * object.  */
gpgme_off_t gpgme_data_seek (gpgme_data_t dh, gpgme_off_t offset, int whence);

/* Create a new data buffer and return it in R_DH.  */
gpgme_error_t gpgme_data_new (gpgme_data_t *r_dh);

/* Destroy the data buffer DH.  */
void gpgme_data_release (gpgme_data_t dh);

/* Create a new data buffer filled with SIZE bytes starting from
 * BUFFER.  If COPY is zero, copying is delayed until necessary, and
 * the data is taken from the original location when needed.  */
gpgme_error_t gpgme_data_new_from_mem (gpgme_data_t *r_dh,
				       const char *buffer, size_t size,
				       int copy);

/* Destroy the data buffer DH and return a pointer to its content.
 * The memory has be to released with gpgme_free() by the user.  It's
 * size is returned in R_LEN.  */
char *gpgme_data_release_and_get_mem (gpgme_data_t dh, size_t *r_len);

/* Release the memory returned by gpgme_data_release_and_get_mem() and
 * some other functions.  */
void gpgme_free (void *buffer);

gpgme_error_t gpgme_data_new_from_cbs (gpgme_data_t *dh,
				       gpgme_data_cbs_t cbs,
				       void *handle);

gpgme_error_t gpgme_data_new_from_fd (gpgme_data_t *dh, int fd);

gpgme_error_t gpgme_data_new_from_stream (gpgme_data_t *dh, FILE *stream);
gpgme_error_t gpgme_data_new_from_estream (gpgme_data_t *r_dh,
                                           gpgrt_stream_t stream);

/* Return the encoding attribute of the data buffer DH */
gpgme_data_encoding_t gpgme_data_get_encoding (gpgme_data_t dh);

/* Set the encoding attribute of data buffer DH to ENC */
gpgme_error_t gpgme_data_set_encoding (gpgme_data_t dh,
				       gpgme_data_encoding_t enc);

/* Get the file name associated with the data object with handle DH, or
 * NULL if there is none.  */
char *gpgme_data_get_file_name (gpgme_data_t dh);

/* Set the file name associated with the data object with handle DH to
 * FILE_NAME.  */
gpgme_error_t gpgme_data_set_file_name (gpgme_data_t dh,
					const char *file_name);

/* Set a flag for the data object DH.  See the manual for details.  */
gpg_error_t gpgme_data_set_flag (gpgme_data_t dh,
                                 const char *name, const char *value);

/* Try to identify the type of the data in DH.  */
gpgme_data_type_t gpgme_data_identify (gpgme_data_t dh, int reserved);


/* Create a new data buffer filled with the content of file FNAME.
 * COPY must be non-zero.  For delayed read, please use
 * gpgme_data_new_from_fd or gpgme_data_new_from_stream instead.  */
gpgme_error_t gpgme_data_new_from_file (gpgme_data_t *r_dh,
					const char *fname,
					int copy);

/* Create a new data buffer filled with LENGTH bytes starting from
 * OFFSET within the file FNAME or stream FP (exactly one must be
 * non-zero).  */
gpgme_error_t gpgme_data_new_from_filepart (gpgme_data_t *r_dh,
					    const char *fname, FILE *fp,
					    gpgme_off_t offset, size_t length);

/* Convenience function to do a gpgme_data_seek (dh, 0, SEEK_SET).  */
gpgme_error_t gpgme_data_rewind (gpgme_data_t dh);



/*
 * Key and trust functions.
 */

/* Get the key with the fingerprint FPR from the crypto backend.  If
 * SECRET is true, get the secret key.  */
gpgme_error_t gpgme_get_key (gpgme_ctx_t ctx, const char *fpr,
			     gpgme_key_t *r_key, int secret);

/* Create a dummy key to specify an email address.  */
gpgme_error_t gpgme_key_from_uid (gpgme_key_t *key, const char *name);

/* Acquire a reference to KEY.  */
void gpgme_key_ref (gpgme_key_t key);

/* Release a reference to KEY.  If this was the last one the key is
 * destroyed.  */
void gpgme_key_unref (gpgme_key_t key);
void gpgme_key_release (gpgme_key_t key);



/*
 * Encryption.
 */

/* An object to return results from an encryption operation.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_encrypt_result
{
  /* The list of invalid recipients.  */
  gpgme_invalid_key_t invalid_recipients;
};
typedef struct _gpgme_op_encrypt_result *gpgme_encrypt_result_t;

/* Retrieve a pointer to the result of the encrypt operation.  */
gpgme_encrypt_result_t gpgme_op_encrypt_result (gpgme_ctx_t ctx);

/* The valid encryption flags.  */
typedef enum
  {
    GPGME_ENCRYPT_ALWAYS_TRUST = 1,
    GPGME_ENCRYPT_NO_ENCRYPT_TO = 2,
    GPGME_ENCRYPT_PREPARE = 4,
    GPGME_ENCRYPT_EXPECT_SIGN = 8,
    GPGME_ENCRYPT_NO_COMPRESS = 16,
    GPGME_ENCRYPT_SYMMETRIC = 32,
    GPGME_ENCRYPT_THROW_KEYIDS = 64,
    GPGME_ENCRYPT_WRAP = 128,
    GPGME_ENCRYPT_WANT_ADDRESS = 256,
    GPGME_ENCRYPT_ARCHIVE = 512
  }
gpgme_encrypt_flags_t;

/* Encrypt plaintext PLAIN within CTX for the recipients RECP and
 * store the resulting ciphertext in CIPHER.  */
gpgme_error_t gpgme_op_encrypt_start (gpgme_ctx_t ctx, gpgme_key_t recp[],
				      gpgme_encrypt_flags_t flags,
				      gpgme_data_t plain,
                                      gpgme_data_t cipher);
gpgme_error_t gpgme_op_encrypt (gpgme_ctx_t ctx, gpgme_key_t recp[],
				gpgme_encrypt_flags_t flags,
				gpgme_data_t plain,
                                gpgme_data_t cipher);
gpgme_error_t gpgme_op_encrypt_ext_start (gpgme_ctx_t ctx, gpgme_key_t recp[],
                                          const char *recpstring,
                                          gpgme_encrypt_flags_t flags,
                                          gpgme_data_t plain,
                                          gpgme_data_t cipher);
gpgme_error_t gpgme_op_encrypt_ext (gpgme_ctx_t ctx, gpgme_key_t recp[],
                                    const char *recpstring,
                                    gpgme_encrypt_flags_t flags,
                                    gpgme_data_t plain,
                                    gpgme_data_t cipher);

/* Encrypt plaintext PLAIN within CTX for the recipients RECP and
 * store the resulting ciphertext in CIPHER.  Also sign the ciphertext
 * with the signers in CTX.  */
gpgme_error_t gpgme_op_encrypt_sign_start (gpgme_ctx_t ctx,
					   gpgme_key_t recp[],
					   gpgme_encrypt_flags_t flags,
					   gpgme_data_t plain,
					   gpgme_data_t cipher);
gpgme_error_t gpgme_op_encrypt_sign (gpgme_ctx_t ctx, gpgme_key_t recp[],
				     gpgme_encrypt_flags_t flags,
				     gpgme_data_t plain,
                                     gpgme_data_t cipher);
gpgme_error_t gpgme_op_encrypt_sign_ext_start (gpgme_ctx_t ctx,
                                               gpgme_key_t recp[],
                                               const char *recpstring,
                                               gpgme_encrypt_flags_t flags,
                                               gpgme_data_t plain,
                                               gpgme_data_t cipher);
gpgme_error_t gpgme_op_encrypt_sign_ext (gpgme_ctx_t ctx, gpgme_key_t recp[],
                                         const char *recpstring,
                                         gpgme_encrypt_flags_t flags,
                                         gpgme_data_t plain,
                                         gpgme_data_t cipher);


/*
 * Decryption.
 */

/* An object to hold information about a recipient.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_recipient
{
  struct _gpgme_recipient *next;

  /* The key ID of key for which the text was encrypted.  */
  char *keyid;

  /* Internal to GPGME, do not use.  */
  char _keyid[16 + 1];

  /* The public key algorithm of the recipient key.  */
  gpgme_pubkey_algo_t pubkey_algo;

  /* The status of the recipient.  */
  gpgme_error_t status;
};
typedef struct _gpgme_recipient *gpgme_recipient_t;


/* An object to return results from a decryption operation.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_decrypt_result
{
  char *unsupported_algorithm;

  /* Key should not have been used for encryption.  */
  unsigned int wrong_key_usage : 1;

  /* True if the message was encrypted in compliance to the de-vs
   * mode.  */
  unsigned int is_de_vs : 1;

  /* The message claims that the content is a MIME object.  */
  unsigned int is_mime : 1;

  /* The message was made by a legacy algorithm without any integrity
   * protection.  This might be an old but legitimate message. */
  unsigned int legacy_cipher_nomdc : 1;

  /* Internal to GPGME, do not use.  */
  int _unused : 28;

  gpgme_recipient_t recipients;

  /* The original file name of the plaintext message, if
   * available.  */
  char *file_name;

  /* A textual representation of the session key used to decrypt the
   * message, if available */
  char *session_key;

  /* A string with the symmetric encryption algorithm and mode using
   * the format "<algo>.<mode>".  */
  char *symkey_algo;
};
typedef struct _gpgme_op_decrypt_result *gpgme_decrypt_result_t;


/* Retrieve a pointer to the result of the decrypt operation.  */
gpgme_decrypt_result_t gpgme_op_decrypt_result (gpgme_ctx_t ctx);


/* The valid decryption flags.  */
typedef enum
  {
    GPGME_DECRYPT_VERIFY = 1,
    GPGME_DECRYPT_ARCHIVE = 2,
    GPGME_DECRYPT_UNWRAP = 128
  }
gpgme_decrypt_flags_t;


/* Decrypt ciphertext CIPHER within CTX and store the resulting
 * plaintext in PLAIN.  */
gpgme_error_t gpgme_op_decrypt_start (gpgme_ctx_t ctx, gpgme_data_t cipher,
				      gpgme_data_t plain);
gpgme_error_t gpgme_op_decrypt (gpgme_ctx_t ctx,
				gpgme_data_t cipher, gpgme_data_t plain);

/* Decrypt ciphertext CIPHER and make a signature verification within
 * CTX and store the resulting plaintext in PLAIN.  */
gpgme_error_t gpgme_op_decrypt_verify_start (gpgme_ctx_t ctx,
					     gpgme_data_t cipher,
					     gpgme_data_t plain);
gpgme_error_t gpgme_op_decrypt_verify (gpgme_ctx_t ctx, gpgme_data_t cipher,
				       gpgme_data_t plain);

/* Decrypt ciphertext CIPHER within CTX and store the resulting
 * plaintext in PLAIN.  With the flag GPGME_DECRYPT_VERIFY also do a
 * signature verification pn the plaintext.  */
gpgme_error_t gpgme_op_decrypt_ext_start (gpgme_ctx_t ctx,
                                          gpgme_decrypt_flags_t flags,
                                          gpgme_data_t cipher,
                                          gpgme_data_t plain);
gpgme_error_t gpgme_op_decrypt_ext (gpgme_ctx_t ctx,
                                    gpgme_decrypt_flags_t flags,
                                    gpgme_data_t cipher,
                                    gpgme_data_t plain);



/*
 * Signing.
 */

/* An object with signatures data.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_new_signature
{
  struct _gpgme_new_signature *next;

  /* The type of the signature.  */
  gpgme_sig_mode_t type;

  /* The public key algorithm used to create the signature.  */
  gpgme_pubkey_algo_t pubkey_algo;

  /* The hash algorithm used to create the signature.  */
  gpgme_hash_algo_t hash_algo;

  /* Internal to GPGME, do not use.  Must be set to the same value as
   * CLASS below.  */
  unsigned long _obsolete_class;

  /* Signature creation time.  */
  long int timestamp;

  /* The fingerprint of the signature.  */
  char *fpr;

  /* Deprecated; use SIG_CLASS instead.  */
#ifdef _GPGME_OBSOLETE_SOME_SYMBOLS
  unsigned int _obsolete_class_2;
#else
  unsigned int class _GPGME_DEPRECATED_OUTSIDE_GPGME(0,4);
#endif

  /* Crypto backend specific signature class.  */
  unsigned int sig_class;
};
typedef struct _gpgme_new_signature *gpgme_new_signature_t;


/* An object to return results from a signing operation.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_sign_result
{
  /* The list of invalid signers.  */
  gpgme_invalid_key_t invalid_signers;
  gpgme_new_signature_t signatures;
};
typedef struct _gpgme_op_sign_result *gpgme_sign_result_t;


/* Retrieve a pointer to the result of the signing operation.  */
gpgme_sign_result_t gpgme_op_sign_result (gpgme_ctx_t ctx);

/* Sign the plaintext PLAIN and store the signature in SIG.  */
gpgme_error_t gpgme_op_sign_start (gpgme_ctx_t ctx,
				   gpgme_data_t plain, gpgme_data_t sig,
				   gpgme_sig_mode_t flags);
gpgme_error_t gpgme_op_sign (gpgme_ctx_t ctx,
			     gpgme_data_t plain, gpgme_data_t sig,
			     gpgme_sig_mode_t flags);


/*
 * Verify.
 */

/* Flags used for the SUMMARY field in a gpgme_signature_t.  */
typedef enum
  {
    GPGME_SIGSUM_VALID       = 0x0001,  /* The signature is fully valid.  */
    GPGME_SIGSUM_GREEN       = 0x0002,  /* The signature is good.  */
    GPGME_SIGSUM_RED         = 0x0004,  /* The signature is bad.  */
    GPGME_SIGSUM_KEY_REVOKED = 0x0010,  /* One key has been revoked.  */
    GPGME_SIGSUM_KEY_EXPIRED = 0x0020,  /* One key has expired.  */
    GPGME_SIGSUM_SIG_EXPIRED = 0x0040,  /* The signature has expired.  */
    GPGME_SIGSUM_KEY_MISSING = 0x0080,  /* Can't verify: key missing.  */
    GPGME_SIGSUM_CRL_MISSING = 0x0100,  /* CRL not available.  */
    GPGME_SIGSUM_CRL_TOO_OLD = 0x0200,  /* Available CRL is too old.  */
    GPGME_SIGSUM_BAD_POLICY  = 0x0400,  /* A policy was not met.  */
    GPGME_SIGSUM_SYS_ERROR   = 0x0800,  /* A system error occurred.  */
    GPGME_SIGSUM_TOFU_CONFLICT=0x1000   /* Tofu conflict detected.  */
  }
gpgme_sigsum_t;


/* An object to hold the verification status of a signature.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_signature
{
  struct _gpgme_signature *next;

  /* A summary of the signature status.  */
  gpgme_sigsum_t summary;

  /* The fingerprint of the signature.  This can be a subkey.  */
  char *fpr;

  /* The status of the signature.  */
  gpgme_error_t status;

  /* Notation data and policy URLs.  */
  gpgme_sig_notation_t notations;

  /* Signature creation time.  */
  unsigned long timestamp;

  /* Signature expiration time or 0.  */
  unsigned long exp_timestamp;

  /* Key should not have been used for signing.  */
  unsigned int wrong_key_usage : 1;

  /* PKA status: 0 = not available, 1 = bad, 2 = okay, 3 = RFU. */
  unsigned int pka_trust : 2;

  /* Validity has been verified using the chain model. */
  unsigned int chain_model : 1;

  /* True if the signature is in compliance to the de-vs mode.  */
  unsigned int is_de_vs : 1;

  /* Internal to GPGME, do not use.  */
  int _unused : 27;

  gpgme_validity_t validity;
  gpgme_error_t validity_reason;

  /* The public key algorithm used to create the signature.  */
  gpgme_pubkey_algo_t pubkey_algo;

  /* The hash algorithm used to create the signature.  */
  gpgme_hash_algo_t hash_algo;

  /* The mailbox from the PKA information or NULL. */
  char *pka_address;

  /* If non-NULL, a possible incomplete key object with the data
   * available for the signature.  */
  gpgme_key_t key;
};
typedef struct _gpgme_signature *gpgme_signature_t;


/* An object to return the results of a verify operation.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_verify_result
{
  gpgme_signature_t signatures;

  /* The original file name of the plaintext message, if available.
   * Warning: This information is not covered by the signature.  */
  char *file_name;

  /* The message claims that the content is a MIME object.  */
  /* Warning: This flag is not covered by the signature.  */
  unsigned int is_mime : 1;

  /* Internal to GPGME; do not use.  */
  unsigned int _unused : 31;
};
typedef struct _gpgme_op_verify_result *gpgme_verify_result_t;


/* Retrieve a pointer to the result of the verify operation.  */
gpgme_verify_result_t gpgme_op_verify_result (gpgme_ctx_t ctx);

/* The valid verify flags.  */
typedef enum
  {
    GPGME_VERIFY_ARCHIVE = 1
  }
gpgme_verify_flags_t;

/* Verify within CTX that SIG is a valid signature for TEXT.  */
gpgme_error_t gpgme_op_verify_start (gpgme_ctx_t ctx, gpgme_data_t sig,
				     gpgme_data_t signed_text,
				     gpgme_data_t plaintext);
gpgme_error_t gpgme_op_verify (gpgme_ctx_t ctx, gpgme_data_t sig,
			       gpgme_data_t signed_text,
			       gpgme_data_t plaintext);
gpgme_error_t gpgme_op_verify_ext_start (gpgme_ctx_t ctx,
                                         gpgme_verify_flags_t flags,
                                         gpgme_data_t sig,
                                         gpgme_data_t signed_text,
                                         gpgme_data_t plaintext);
gpgme_error_t gpgme_op_verify_ext (gpgme_ctx_t ctx,
                                   gpgme_verify_flags_t flags,
                                   gpgme_data_t sig,
                                   gpgme_data_t signed_text,
                                   gpgme_data_t plaintext);


/*
 * Import/Export
 */

#define GPGME_IMPORT_NEW	1  /* The key was new.  */
#define GPGME_IMPORT_UID	2  /* The key contained new user IDs.  */
#define GPGME_IMPORT_SIG	4  /* The key contained new signatures.  */
#define GPGME_IMPORT_SUBKEY	8  /* The key contained new sub keys.  */
#define GPGME_IMPORT_SECRET    16  /* The key contained a secret key.  */


/* An object to hold results for one imported key.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_import_status
{
  struct _gpgme_import_status *next;

  /* Fingerprint.  */
  char *fpr;

  /* If a problem occurred, the reason why the key could not be
     imported.  Otherwise GPGME_No_Error.  */
  gpgme_error_t result;

  /* The result of the import, the GPGME_IMPORT_* values bit-wise
     ORed.  0 means the key was already known and no new components
     have been added.  */
  unsigned int status;
};
typedef struct _gpgme_import_status *gpgme_import_status_t;


/* Import result object.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_import_result
{
  /* Number of considered keys.  */
  int considered;

  /* Keys without user ID.  */
  int no_user_id;

  /* Imported keys.  */
  int imported;

  /* Imported RSA keys.  */
  int imported_rsa;

  /* Unchanged keys.  */
  int unchanged;

  /* Number of new user ids.  */
  int new_user_ids;

  /* Number of new sub keys.  */
  int new_sub_keys;

  /* Number of new signatures.  */
  int new_signatures;

  /* Number of new revocations.  */
  int new_revocations;

  /* Number of secret keys read.  */
  int secret_read;

  /* Number of secret keys imported.  */
  int secret_imported;

  /* Number of secret keys unchanged.  */
  int secret_unchanged;

  /* Number of new keys skipped.  */
  int skipped_new_keys;

  /* Number of keys not imported.  */
  int not_imported;

  /* List of keys for which an import was attempted.  */
  gpgme_import_status_t imports;

  /* Number of v3 keys skipped.  */
  int skipped_v3_keys;
};
typedef struct _gpgme_op_import_result *gpgme_import_result_t;


/* Retrieve a pointer to the result of the import operation.  */
gpgme_import_result_t gpgme_op_import_result (gpgme_ctx_t ctx);

/* Import the key in KEYDATA into the keyring.  */
gpgme_error_t gpgme_op_import_start (gpgme_ctx_t ctx, gpgme_data_t keydata);
gpgme_error_t gpgme_op_import (gpgme_ctx_t ctx, gpgme_data_t keydata);

/* Import the keys from the array KEYS into the keyring.  */
gpgme_error_t gpgme_op_import_keys_start (gpgme_ctx_t ctx, gpgme_key_t keys[]);
gpgme_error_t gpgme_op_import_keys (gpgme_ctx_t ctx, gpgme_key_t keys[]);

/* Import the keys given by the array KEYIDS from a keyserver into the
 * keyring.  */
gpgme_error_t gpgme_op_receive_keys_start (gpgme_ctx_t ctx,
                                           const char *keyids[]);
gpgme_error_t gpgme_op_receive_keys (gpgme_ctx_t ctx, const char *keyids[]);


/* Export the keys found by PATTERN into KEYDATA.  */
gpgme_error_t gpgme_op_export_start (gpgme_ctx_t ctx, const char *pattern,
				     gpgme_export_mode_t mode,
				     gpgme_data_t keydata);
gpgme_error_t gpgme_op_export (gpgme_ctx_t ctx, const char *pattern,
			       gpgme_export_mode_t mode,
                               gpgme_data_t keydata);

gpgme_error_t gpgme_op_export_ext_start (gpgme_ctx_t ctx,
					 const char *pattern[],
					 gpgme_export_mode_t mode,
					 gpgme_data_t keydata);
gpgme_error_t gpgme_op_export_ext (gpgme_ctx_t ctx, const char *pattern[],
				   gpgme_export_mode_t mode,
				   gpgme_data_t keydata);

/* Export the keys from the array KEYS into KEYDATA.  */
gpgme_error_t gpgme_op_export_keys_start (gpgme_ctx_t ctx,
                                          gpgme_key_t keys[],
                                          gpgme_export_mode_t mode,
                                          gpgme_data_t keydata);
gpgme_error_t gpgme_op_export_keys (gpgme_ctx_t ctx,
                                    gpgme_key_t keys[],
                                    gpgme_export_mode_t mode,
                                    gpgme_data_t keydata);



/*
 * Key generation.
 */

/* Flags for the key creation functions.  */
#define GPGME_CREATE_SIGN       (1 << 0)  /* Allow usage: signing.     */
#define GPGME_CREATE_ENCR       (1 << 1)  /* Allow usage: encryption.  */
#define GPGME_CREATE_CERT       (1 << 2)  /* Allow usage: certification.  */
#define GPGME_CREATE_AUTH       (1 << 3)  /* Allow usage: authentication.  */
#define GPGME_CREATE_NOPASSWD   (1 << 7)  /* Create w/o passphrase.    */
#define GPGME_CREATE_SELFSIGNED (1 << 8)  /* Create self-signed cert.  */
#define GPGME_CREATE_NOSTORE    (1 << 9)  /* Do not store the key.     */
#define GPGME_CREATE_WANTPUB    (1 << 10) /* Return the public key.    */
#define GPGME_CREATE_WANTSEC    (1 << 11) /* Return the secret key.    */
#define GPGME_CREATE_FORCE      (1 << 12) /* Force creation.           */
#define GPGME_CREATE_NOEXPIRE   (1 << 13) /* Create w/o expiration.    */


/* An object to return result from a key generation.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_genkey_result
{
  /* A primary key was generated.  */
  unsigned int primary : 1;

  /* A sub key was generated.  */
  unsigned int sub : 1;

  /* A user id was generated.  */
  unsigned int uid : 1;

  /* Internal to GPGME, do not use.  */
  unsigned int _unused : 29;

  /* The fingerprint of the generated key.  */
  char *fpr;

  /* A memory data object with the created public key.  Only set when
   * GPGME_CREATE_WANTPUB has been used. */
  gpgme_data_t pubkey;

  /* A memory data object with the created secret key.  Only set when
   * GPGME_CREATE_WANTSEC has been used. */
  gpgme_data_t seckey;
};
typedef struct _gpgme_op_genkey_result *gpgme_genkey_result_t;


/* Generate a new keypair and add it to the keyring.  PUBKEY and
 * SECKEY should be null for now.  PARMS specifies what keys should be
 * generated.  */
gpgme_error_t gpgme_op_genkey_start (gpgme_ctx_t ctx, const char *parms,
				     gpgme_data_t pubkey, gpgme_data_t seckey);
gpgme_error_t gpgme_op_genkey (gpgme_ctx_t ctx, const char *parms,
			       gpgme_data_t pubkey, gpgme_data_t seckey);

/* Generate a key pair using the modern interface.  */
gpgme_error_t gpgme_op_createkey_start (gpgme_ctx_t ctx,
                                        const char *userid,
                                        const char *algo,
                                        unsigned long reserved,
                                        unsigned long expires,
                                        gpgme_key_t certkey,
                                        unsigned int flags);
gpgme_error_t gpgme_op_createkey       (gpgme_ctx_t ctx,
                                        const char *userid,
                                        const char *algo,
                                        unsigned long reserved,
                                        unsigned long expires,
                                        gpgme_key_t certkey,
                                        unsigned int flags);
/* Add a new subkey to KEY.  */
gpgme_error_t gpgme_op_createsubkey_start (gpgme_ctx_t ctx,
                                           gpgme_key_t key,
                                           const char *algo,
                                           unsigned long reserved,
                                           unsigned long expires,
                                           unsigned int flags);
gpgme_error_t gpgme_op_createsubkey       (gpgme_ctx_t ctx,
                                           gpgme_key_t key,
                                           const char *algo,
                                           unsigned long reserved,
                                           unsigned long expires,
                                           unsigned int flags);

/* Add USERID to an existing KEY.  */
gpgme_error_t gpgme_op_adduid_start (gpgme_ctx_t ctx,
                                     gpgme_key_t key, const char *userid,
                                     unsigned int reserved);
gpgme_error_t gpgme_op_adduid       (gpgme_ctx_t ctx,
                                     gpgme_key_t key, const char *userid,
                                     unsigned int reserved);

/* Revoke a USERID from a KEY.  */
gpgme_error_t gpgme_op_revuid_start (gpgme_ctx_t ctx,
                                     gpgme_key_t key, const char *userid,
                                     unsigned int reserved);
gpgme_error_t gpgme_op_revuid       (gpgme_ctx_t ctx,
                                     gpgme_key_t key, const char *userid,
                                     unsigned int reserved);

/* Set a flag on the USERID of KEY.  See the manual for supported flags.  */
gpgme_error_t gpgme_op_set_uid_flag_start (gpgme_ctx_t ctx,
                                           gpgme_key_t key, const char *userid,
                                           const char *name, const char *value);
gpgme_error_t gpgme_op_set_uid_flag       (gpgme_ctx_t ctx,
                                           gpgme_key_t key, const char *userid,
                                           const char *name, const char *value);

/* Change the expiry of a key.  */
gpgme_error_t gpgme_op_setexpire_start (gpgme_ctx_t ctx,
                                        gpgme_key_t key, unsigned long expires,
                                        const char *subfprs, unsigned int reserved);
gpgme_error_t gpgme_op_setexpire       (gpgme_ctx_t ctx,
                                        gpgme_key_t key, unsigned long expires,
                                        const char *subfprs, unsigned int reserved);

/* Retrieve a pointer to the result of a genkey, createkey, or
 * createsubkey operation.  */
gpgme_genkey_result_t gpgme_op_genkey_result (gpgme_ctx_t ctx);


/* Delete KEY from the keyring.  If ALLOW_SECRET is non-zero, secret
 * keys are also deleted.  */
gpgme_error_t gpgme_op_delete_start (gpgme_ctx_t ctx, const gpgme_key_t key,
				     int allow_secret);
gpgme_error_t gpgme_op_delete (gpgme_ctx_t ctx, const gpgme_key_t key,
			       int allow_secret);

/* Flags for the key delete functions.  */
#define GPGME_DELETE_ALLOW_SECRET (1 << 0)  /* Also delete secret key.     */
#define GPGME_DELETE_FORCE        (1 << 1)  /* Do not ask user to confirm.  */

gpgme_error_t gpgme_op_delete_ext_start (gpgme_ctx_t ctx, const gpgme_key_t key,
					 unsigned int flags);
gpgme_error_t gpgme_op_delete_ext (gpgme_ctx_t ctx, const gpgme_key_t key,
				   unsigned int flags);


/*
 * Key signing interface
 */

/* Flags for the key signing functions.  */
#define GPGME_KEYSIGN_LOCAL     (1 << 7)  /* Create a local signature.  */
#define GPGME_KEYSIGN_LFSEP     (1 << 8)  /* Indicate LF separated user ids. */
#define GPGME_KEYSIGN_NOEXPIRE  (1 << 9)  /* Force no expiration.  */
#define GPGME_KEYSIGN_FORCE     (1 << 10) /* Force creation.  */


/* Sign the USERID of KEY using the current set of signers.  */
gpgme_error_t gpgme_op_keysign_start (gpgme_ctx_t ctx,
                                      gpgme_key_t key, const char *userid,
                                      unsigned long expires,
                                      unsigned int flags);
gpgme_error_t gpgme_op_keysign       (gpgme_ctx_t ctx,
                                      gpgme_key_t key, const char *userid,
                                      unsigned long expires,
                                      unsigned int flags);


/* Flags for the signature revoking functions.  */
#define GPGME_REVSIG_LFSEP   (1 << 8)  /* Indicate LF separated user ids. */

/* Revoke the signatures made with SIGNING_KEY on the USERID(s) of KEY.  */
gpgme_error_t gpgme_op_revsig_start (gpgme_ctx_t ctx,
                                     gpgme_key_t key,
                                     gpgme_key_t signing_key,
                                     const char *userid,
                                     unsigned int flags);
gpgme_error_t gpgme_op_revsig       (gpgme_ctx_t ctx,
                                     gpgme_key_t key,
                                     gpgme_key_t signing_key,
                                     const char *userid,
                                     unsigned int flags);


/*
 * Key edit interface
 */

/* Flags to select the mode of the interact.  */
#define GPGME_INTERACT_CARD   (1 << 0)  /* Use --card-edit mode. */


/* Edit the KEY.  Send status and command requests to FNC and
   output of edit commands to OUT.  */
gpgme_error_t gpgme_op_interact_start (gpgme_ctx_t ctx,
                                       gpgme_key_t key,
                                       unsigned int flags,
                                       gpgme_interact_cb_t fnc,
                                       void *fnc_value,
                                       gpgme_data_t out);
gpgme_error_t gpgme_op_interact (gpgme_ctx_t ctx, gpgme_key_t key,
                                 unsigned int flags,
                                 gpgme_interact_cb_t fnc,
                                 void *fnc_value,
                                 gpgme_data_t out);


/* Set the Tofu policy of KEY to POLCIY.  */
gpgme_error_t gpgme_op_tofu_policy_start (gpgme_ctx_t ctx,
                                          gpgme_key_t key,
                                          gpgme_tofu_policy_t policy);
gpgme_error_t gpgme_op_tofu_policy       (gpgme_ctx_t ctx,
                                          gpgme_key_t key,
                                          gpgme_tofu_policy_t policy);




/*
 * Key listing
 */

/* An object to return results from a key listing operation.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_keylist_result
{
  unsigned int truncated : 1;

  /* Internal to GPGME, do not use.  */
  unsigned int _unused : 31;
};
typedef struct _gpgme_op_keylist_result *gpgme_keylist_result_t;

/* Retrieve a pointer to the result of the key listing operation.  */
gpgme_keylist_result_t gpgme_op_keylist_result (gpgme_ctx_t ctx);

/* Start a keylist operation within CTX, searching for keys which
 * match PATTERN.  If SECRET_ONLY is true, only secret keys are
 * returned.  */
gpgme_error_t gpgme_op_keylist_start (gpgme_ctx_t ctx, const char *pattern,
				      int secret_only);
gpgme_error_t gpgme_op_keylist_ext_start (gpgme_ctx_t ctx,
					  const char *pattern[],
					  int secret_only, int reserved);

/* List the keys contained in DATA.  */
gpgme_error_t gpgme_op_keylist_from_data_start (gpgme_ctx_t ctx,
                                                gpgme_data_t data,
                                                int reserved);

/* Return the next key from the keylist in R_KEY.  */
gpgme_error_t gpgme_op_keylist_next (gpgme_ctx_t ctx, gpgme_key_t *r_key);

/* Terminate a pending keylist operation within CTX.  */
gpgme_error_t gpgme_op_keylist_end (gpgme_ctx_t ctx);



/*
 * Protecting keys
 */

/* Change the passphrase for KEY.  FLAGS is reserved for future use
 * and must be passed as 0.  */
gpgme_error_t gpgme_op_passwd_start (gpgme_ctx_t ctx, gpgme_key_t key,
                                     unsigned int flags);
gpgme_error_t gpgme_op_passwd (gpgme_ctx_t ctx, gpgme_key_t key,
                               unsigned int flags);



/*
 * Trust items and operations.  DO NOT USE.
 * Note: This does not work because the experimental support in the
 * GnuPG engine has been removed a very long time; for API and ABI
 * compatibilty we keep the functions but let them return an error.
 * See https://dev.gnupg.org/T4834
 */
struct _gpgme_trust_item
{
  unsigned int _refs;
  char *keyid;
  char _keyid[16 + 1];
  int type;
  int level;
  char *owner_trust;
  char _owner_trust[2];
  char *validity;
  char _validity[2];
  char *name;
};
typedef struct _gpgme_trust_item *gpgme_trust_item_t;
gpgme_error_t gpgme_op_trustlist_start (gpgme_ctx_t ctx,
					const char *pattern, int max_level);
gpgme_error_t gpgme_op_trustlist_next (gpgme_ctx_t ctx,
				       gpgme_trust_item_t *r_item);
gpgme_error_t gpgme_op_trustlist_end (gpgme_ctx_t ctx);
void gpgme_trust_item_ref (gpgme_trust_item_t item);
void gpgme_trust_item_unref (gpgme_trust_item_t item);



/*
 * Audit log
 */

/* Return the auditlog for the current session.  This may be called
   after a successful or failed operation.  If no audit log is
   available GPG_ERR_NO_DATA is returned.  */
gpgme_error_t gpgme_op_getauditlog_start (gpgme_ctx_t ctx, gpgme_data_t output,
                                          unsigned int flags);
gpgme_error_t gpgme_op_getauditlog (gpgme_ctx_t ctx, gpgme_data_t output,
                                    unsigned int flags);



/*
 * Spawn interface
 */

/* Flags for the spawn operations.  */
#define GPGME_SPAWN_DETACHED      1
#define GPGME_SPAWN_ALLOW_SET_FG  2
#define GPGME_SPAWN_SHOW_WINDOW   4


/* Run the command FILE with the arguments in ARGV.  Connect stdin to
 * DATAIN, stdout to DATAOUT, and STDERR to DATAERR.  If one the data
 * streams is NULL, connect to /dev/null instead.  */
gpgme_error_t gpgme_op_spawn_start (gpgme_ctx_t ctx,
                                    const char *file, const char *argv[],
                                    gpgme_data_t datain,
                                    gpgme_data_t dataout, gpgme_data_t dataerr,
                                    unsigned int flags);
gpgme_error_t gpgme_op_spawn (gpgme_ctx_t ctx,
                              const char *file, const char *argv[],
                              gpgme_data_t datain,
                              gpgme_data_t dataout, gpgme_data_t dataerr,
                              unsigned int flags);


/*
 * Low-level Assuan protocol access.
 */

typedef gpgme_error_t (*gpgme_assuan_data_cb_t)
     (void *opaque, const void *data, size_t datalen);

typedef gpgme_error_t (*gpgme_assuan_inquire_cb_t)
     (void *opaque, const char *name, const char *args,
      gpgme_data_t *r_data);

typedef gpgme_error_t (*gpgme_assuan_status_cb_t)
     (void *opaque, const char *status, const char *args);

/* Send the Assuan COMMAND and return results via the callbacks.
 * Asynchronous variant. */
gpgme_error_t gpgme_op_assuan_transact_start (gpgme_ctx_t ctx,
                                              const char *command,
                                              gpgme_assuan_data_cb_t data_cb,
                                              void *data_cb_value,
                                              gpgme_assuan_inquire_cb_t inq_cb,
                                              void *inq_cb_value,
                                              gpgme_assuan_status_cb_t stat_cb,
                                              void *stat_cb_value);

/* Send the Assuan COMMAND and return results via the callbacks.
 * Synchronous variant. */
gpgme_error_t gpgme_op_assuan_transact_ext (gpgme_ctx_t ctx,
					    const char *command,
					    gpgme_assuan_data_cb_t data_cb,
					    void *data_cb_value,
					    gpgme_assuan_inquire_cb_t inq_cb,
					    void *inq_cb_value,
					    gpgme_assuan_status_cb_t stat_cb,
					    void *stat_cb_value,
					    gpgme_error_t *op_err);


/*
 * Crypto container support.
 */

/* An object to return results from a VFS mount operation.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_vfs_mount_result
{
  char *mount_dir;
};
typedef struct _gpgme_op_vfs_mount_result *gpgme_vfs_mount_result_t;

gpgme_vfs_mount_result_t gpgme_op_vfs_mount_result (gpgme_ctx_t ctx);

/* The container is automatically unmounted when the context is reset
 * or destroyed.  Transmission errors are returned directly,
 * operational errors are returned in OP_ERR.  */
gpgme_error_t gpgme_op_vfs_mount (gpgme_ctx_t ctx, const char *container_file,
				  const char *mount_dir, unsigned int flags,
				  gpgme_error_t *op_err);

gpgme_error_t gpgme_op_vfs_create (gpgme_ctx_t ctx, gpgme_key_t recp[],
				   const char *container_file,
				   unsigned int flags, gpgme_error_t *op_err);


/*
 * Interface to gpgconf(1).
 */

/* The expert level at which a configuration option or group of
 * options should be displayed.  See the gpgconf(1) documentation for
 * more details.  */
typedef enum
  {
    GPGME_CONF_BASIC = 0,
    GPGME_CONF_ADVANCED = 1,
    GPGME_CONF_EXPERT = 2,
    GPGME_CONF_INVISIBLE = 3,
    GPGME_CONF_INTERNAL = 4
  }
gpgme_conf_level_t;


/* The data type of a configuration option argument.  See the gpgconf(1)
 * documentation for more details.  */
typedef enum
  {
    /* Basic types.  */
    GPGME_CONF_NONE = 0,
    GPGME_CONF_STRING = 1,
    GPGME_CONF_INT32 = 2,
    GPGME_CONF_UINT32 = 3,

    /* Complex types.  */
    GPGME_CONF_FILENAME = 32,
    GPGME_CONF_LDAP_SERVER = 33,
    GPGME_CONF_KEY_FPR = 34,
    GPGME_CONF_PUB_KEY = 35,
    GPGME_CONF_SEC_KEY = 36,
    GPGME_CONF_ALIAS_LIST = 37
  }
gpgme_conf_type_t;

/* For now, compatibility.  */
#define GPGME_CONF_PATHNAME GPGME_CONF_FILENAME


/* This represents a single argument for a configuration option.
 * Which of the members of value is used depends on the ALT_TYPE.  */
typedef struct gpgme_conf_arg
{
  struct gpgme_conf_arg *next;
  /* True if the option appears without an (optional) argument.  */
  unsigned int no_arg;
  union
  {
    unsigned int count;
    unsigned int uint32;
    int int32;
    char *string;
  } value;
} *gpgme_conf_arg_t;


/* The flags of a configuration option.  See the gpgconf
 * documentation for details.  */
#define GPGME_CONF_GROUP	(1 << 0)
#define GPGME_CONF_OPTIONAL	(1 << 1)
#define GPGME_CONF_LIST		(1 << 2)
#define GPGME_CONF_RUNTIME	(1 << 3)
#define GPGME_CONF_DEFAULT	(1 << 4)
#define GPGME_CONF_DEFAULT_DESC	(1 << 5)
#define GPGME_CONF_NO_ARG_DESC	(1 << 6)
#define GPGME_CONF_NO_CHANGE	(1 << 7)


/* The representation of a single configuration option.  See the
 * gpg-conf documentation for details.  */
typedef struct gpgme_conf_opt
{
  struct gpgme_conf_opt *next;

  /* The option name.  */
  char *name;

  /* The flags for this option.  */
  unsigned int flags;

  /* The level of this option.  */
  gpgme_conf_level_t level;

  /* The localized description of this option.  */
  char *description;

  /* The type and alternate type of this option.  */
  gpgme_conf_type_t type;
  gpgme_conf_type_t alt_type;

  /* The localized (short) name of the argument, if any.  */
  char *argname;

  /* The default value.  */
  gpgme_conf_arg_t default_value;
  char *default_description;

  /* The default value if the option is not set.  */
  gpgme_conf_arg_t no_arg_value;
  char *no_arg_description;

  /* The current value if the option is set.  */
  gpgme_conf_arg_t value;

  /* The new value, if any.  NULL means reset to default.  */
  int change_value;
  gpgme_conf_arg_t new_value;

  /* Free for application use.  */
  void *user_data;
} *gpgme_conf_opt_t;


/* The representation of a component that can be configured.  See the
 * gpg-conf documentation for details.  */
typedef struct gpgme_conf_comp
{
  struct gpgme_conf_comp *next;

  /* Internal to GPGME, do not use!  */
  gpgme_conf_opt_t *_last_opt_p;

  /* The component name.  */
  char *name;

  /* A human-readable description for the component.  */
  char *description;

  /* The program name (an absolute path to the program).  */
  char *program_name;

  /* A linked list of options for this component.  */
  struct gpgme_conf_opt *options;
} *gpgme_conf_comp_t;


/* Allocate a new gpgme_conf_arg_t.  If VALUE is NULL, a "no arg
 * default" is prepared.  If type is a string type, VALUE should point
 * to the string.  Else, it should point to an unsigned or signed
 * integer respectively.  */
gpgme_error_t gpgme_conf_arg_new (gpgme_conf_arg_t *arg_p,
				  gpgme_conf_type_t type, const void *value);

/* This also releases all chained argument structures!  */
void gpgme_conf_arg_release (gpgme_conf_arg_t arg, gpgme_conf_type_t type);

/* Register a change for the value of OPT to ARG.  If RESET is 1 (do
 * not use any values but 0 or 1), ARG is ignored and the option is
 * not changed (reverting a previous change).  Otherwise, if ARG is
 * NULL, the option is cleared or reset to its default. The change
 * is done with gpgconf's --runtime option to immediately take effect. */
gpgme_error_t gpgme_conf_opt_change (gpgme_conf_opt_t opt, int reset,
				     gpgme_conf_arg_t arg);

/* Release a set of configurations.  */
void gpgme_conf_release (gpgme_conf_comp_t conf);

/* Retrieve the current configurations.  */
gpgme_error_t gpgme_op_conf_load (gpgme_ctx_t ctx, gpgme_conf_comp_t *conf_p);

/* Save the configuration of component comp.  This function does not
   follow chained components!  */
gpgme_error_t gpgme_op_conf_save (gpgme_ctx_t ctx, gpgme_conf_comp_t comp);

/* Retrieve the configured directory.  */
gpgme_error_t gpgme_op_conf_dir(gpgme_ctx_t ctx, const char *what,
				char **result);


/* Information about software versions.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
typedef struct _gpgme_op_query_swdb_result
{
  /* RFU */
  struct _gpgme_op_query_swdb_result *next;

  /* The name of the package (e.g. "gpgme", "gnupg") */
  char *name;

  /* The version number of the installed version.  */
  char *iversion;

  /* The time the online info was created.  */
  unsigned long created;

  /* The time the online info was retrieved.  */
  unsigned long retrieved;

  /* This bit is set if an error occured or some of the information
   * in this structure may not be set.  */
  unsigned int warning : 1;

  /* An update is available.  */
  unsigned int update : 1;

  /* The update is important.  */
  unsigned int urgent : 1;

  /* No information at all available.  */
  unsigned int noinfo : 1;

  /* The package name is not known. */
  unsigned int unknown : 1;

  /* The information here is too old.  */
  unsigned int tooold : 1;

  /* Other error.  */
  unsigned int error : 1;

  unsigned int _reserved : 25;

  /* The version number of the latest released version.  */
  char *version;

  /* The release date of that version.  */
  unsigned long reldate;

} *gpgme_query_swdb_result_t;


/* Run the gpgconf --query-swdb command.  */
gpgme_error_t gpgme_op_query_swdb (gpgme_ctx_t ctx,
                                   const char *name, const char *iversion,
                                   unsigned int reserved);

/* Return the result from the last query_swdb operation.  */
gpgme_query_swdb_result_t gpgme_op_query_swdb_result (gpgme_ctx_t ctx);




/*
 * Various functions.
 */

/* Set special global flags; consult the manual before use.  */
int gpgme_set_global_flag (const char *name, const char *value);

/* Check that the library fulfills the version requirement.  Note:
 * This is here only for the case where a user takes a pointer from
 * the old version of this function.  The new version and macro for
 * run-time checks are below.  */
const char *gpgme_check_version (const char *req_version);

/* Do not call this directly; use the macro below.  */
const char *gpgme_check_version_internal (const char *req_version,
					  size_t offset_sig_validity);

/* Check that the library fulfills the version requirement and check
 * for struct layout mismatch involving bitfields.  */
#define gpgme_check_version(req_version)				\
  gpgme_check_version_internal (req_version,				\
				offsetof (struct _gpgme_signature, validity))

/* Return the default values for various directories.  */
const char *gpgme_get_dirinfo (const char *what);

/* Get the information about the configured and installed engines.  A
 * pointer to the first engine in the statically allocated linked list
 * is returned in *INFO.  If an error occurs, it is returned.  The
 * returned data is valid until the next gpgme_set_engine_info.  */
gpgme_error_t gpgme_get_engine_info (gpgme_engine_info_t *engine_info);

/* Set the default engine info for the protocol PROTO to the file name
 * FILE_NAME and the home directory HOME_DIR.  */
gpgme_error_t gpgme_set_engine_info (gpgme_protocol_t proto,
				     const char *file_name,
				     const char *home_dir);

/* Verify that the engine implementing PROTO is installed and
 * available.  */
gpgme_error_t gpgme_engine_check_version (gpgme_protocol_t proto);


/* Reference counting for result objects.  */
void gpgme_result_ref (void *result);
void gpgme_result_unref (void *result);

/* Return a public key algorithm string (e.g. "rsa2048").  Caller must
 * free using gpgme_free.  */
char *gpgme_pubkey_algo_string (gpgme_subkey_t subkey);

/* Return a statically allocated string with the name of the public
 * key algorithm ALGO, or NULL if that name is not known.  */
const char *gpgme_pubkey_algo_name (gpgme_pubkey_algo_t algo);

/* Return a statically allocated string with the name of the hash
 * algorithm ALGO, or NULL if that name is not known.  */
const char *gpgme_hash_algo_name (gpgme_hash_algo_t algo);

/* Return the addr-spec from a user id.  Caller must free the result
 * with gpgme_free. */
char *gpgme_addrspec_from_uid (const char *uid);



/*
 * Deprecated types, constants and functions.
 */

/* This is a former experimental only features.  The constant is
 * provided to not break existing code in the compiler phase.  */
#define GPGME_EXPORT_MODE_NOUID               128  /* Do not use! */


/* The possible stati for gpgme_op_edit.  The use of that function and
 * these status codes are deprecated in favor of gpgme_op_interact. */
typedef enum
  {
    GPGME_STATUS_EOF = 0,
    /* mkstatus processing starts here */
    GPGME_STATUS_ENTER = 1,
    GPGME_STATUS_LEAVE = 2,
    GPGME_STATUS_ABORT = 3,

    GPGME_STATUS_GOODSIG = 4,
    GPGME_STATUS_BADSIG = 5,
    GPGME_STATUS_ERRSIG = 6,

    GPGME_STATUS_BADARMOR = 7,

    GPGME_STATUS_RSA_OR_IDEA = 8,      /* (legacy) */
    GPGME_STATUS_KEYEXPIRED = 9,
    GPGME_STATUS_KEYREVOKED = 10,

    GPGME_STATUS_TRUST_UNDEFINED = 11,
    GPGME_STATUS_TRUST_NEVER = 12,
    GPGME_STATUS_TRUST_MARGINAL = 13,
    GPGME_STATUS_TRUST_FULLY = 14,
    GPGME_STATUS_TRUST_ULTIMATE = 15,

    GPGME_STATUS_SHM_INFO = 16,        /* (legacy) */
    GPGME_STATUS_SHM_GET = 17,         /* (legacy) */
    GPGME_STATUS_SHM_GET_BOOL = 18,    /* (legacy) */
    GPGME_STATUS_SHM_GET_HIDDEN = 19,  /* (legacy) */

    GPGME_STATUS_NEED_PASSPHRASE = 20,
    GPGME_STATUS_VALIDSIG = 21,
    GPGME_STATUS_SIG_ID = 22,
    GPGME_STATUS_ENC_TO = 23,
    GPGME_STATUS_NODATA = 24,
    GPGME_STATUS_BAD_PASSPHRASE = 25,
    GPGME_STATUS_NO_PUBKEY = 26,
    GPGME_STATUS_NO_SECKEY = 27,
    GPGME_STATUS_NEED_PASSPHRASE_SYM = 28,
    GPGME_STATUS_DECRYPTION_FAILED = 29,
    GPGME_STATUS_DECRYPTION_OKAY = 30,
    GPGME_STATUS_MISSING_PASSPHRASE = 31,
    GPGME_STATUS_GOOD_PASSPHRASE = 32,
    GPGME_STATUS_GOODMDC = 33,
    GPGME_STATUS_BADMDC = 34,
    GPGME_STATUS_ERRMDC = 35,
    GPGME_STATUS_IMPORTED = 36,
    GPGME_STATUS_IMPORT_OK = 37,
    GPGME_STATUS_IMPORT_PROBLEM = 38,
    GPGME_STATUS_IMPORT_RES = 39,
    GPGME_STATUS_FILE_START = 40,
    GPGME_STATUS_FILE_DONE = 41,
    GPGME_STATUS_FILE_ERROR = 42,

    GPGME_STATUS_BEGIN_DECRYPTION = 43,
    GPGME_STATUS_END_DECRYPTION = 44,
    GPGME_STATUS_BEGIN_ENCRYPTION = 45,
    GPGME_STATUS_END_ENCRYPTION = 46,

    GPGME_STATUS_DELETE_PROBLEM = 47,
    GPGME_STATUS_GET_BOOL = 48,
    GPGME_STATUS_GET_LINE = 49,
    GPGME_STATUS_GET_HIDDEN = 50,
    GPGME_STATUS_GOT_IT = 51,
    GPGME_STATUS_PROGRESS = 52,
    GPGME_STATUS_SIG_CREATED = 53,
    GPGME_STATUS_SESSION_KEY = 54,
    GPGME_STATUS_NOTATION_NAME = 55,
    GPGME_STATUS_NOTATION_DATA = 56,
    GPGME_STATUS_POLICY_URL = 57,
    GPGME_STATUS_BEGIN_STREAM = 58,    /* (legacy) */
    GPGME_STATUS_END_STREAM = 59,      /* (legacy) */
    GPGME_STATUS_KEY_CREATED = 60,
    GPGME_STATUS_USERID_HINT = 61,
    GPGME_STATUS_UNEXPECTED = 62,
    GPGME_STATUS_INV_RECP = 63,
    GPGME_STATUS_NO_RECP = 64,
    GPGME_STATUS_ALREADY_SIGNED = 65,
    GPGME_STATUS_SIGEXPIRED = 66,      /* (legacy) */
    GPGME_STATUS_EXPSIG = 67,
    GPGME_STATUS_EXPKEYSIG = 68,
    GPGME_STATUS_TRUNCATED = 69,
    GPGME_STATUS_ERROR = 70,
    GPGME_STATUS_NEWSIG = 71,
    GPGME_STATUS_REVKEYSIG = 72,
    GPGME_STATUS_SIG_SUBPACKET = 73,
    GPGME_STATUS_NEED_PASSPHRASE_PIN = 74,
    GPGME_STATUS_SC_OP_FAILURE = 75,
    GPGME_STATUS_SC_OP_SUCCESS = 76,
    GPGME_STATUS_CARDCTRL = 77,
    GPGME_STATUS_BACKUP_KEY_CREATED = 78,
    GPGME_STATUS_PKA_TRUST_BAD = 79,
    GPGME_STATUS_PKA_TRUST_GOOD = 80,
    GPGME_STATUS_PLAINTEXT = 81,
    GPGME_STATUS_INV_SGNR = 82,
    GPGME_STATUS_NO_SGNR = 83,
    GPGME_STATUS_SUCCESS = 84,
    GPGME_STATUS_DECRYPTION_INFO = 85,
    GPGME_STATUS_PLAINTEXT_LENGTH = 86,
    GPGME_STATUS_MOUNTPOINT = 87,
    GPGME_STATUS_PINENTRY_LAUNCHED = 88,
    GPGME_STATUS_ATTRIBUTE = 89,
    GPGME_STATUS_BEGIN_SIGNING = 90,
    GPGME_STATUS_KEY_NOT_CREATED = 91,
    GPGME_STATUS_INQUIRE_MAXLEN = 92,
    GPGME_STATUS_FAILURE = 93,
    GPGME_STATUS_KEY_CONSIDERED = 94,
    GPGME_STATUS_TOFU_USER = 95,
    GPGME_STATUS_TOFU_STATS = 96,
    GPGME_STATUS_TOFU_STATS_LONG = 97,
    GPGME_STATUS_NOTATION_FLAGS = 98,
    GPGME_STATUS_DECRYPTION_COMPLIANCE_MODE = 99,
    GPGME_STATUS_VERIFICATION_COMPLIANCE_MODE = 100,
    GPGME_STATUS_CANCELED_BY_USER = 101
  }
gpgme_status_code_t;

/* The callback type used by the deprecated functions gpgme_op_edit
 * and gpgme_op_card_edit.  */
typedef gpgme_error_t (*gpgme_edit_cb_t) (void *opaque,
					  gpgme_status_code_t status,
					  const char *args, int fd);

gpgme_error_t gpgme_op_edit_start (gpgme_ctx_t ctx, gpgme_key_t key,
				   gpgme_edit_cb_t fnc, void *fnc_value,
				   gpgme_data_t out) _GPGME_DEPRECATED(1,7);
gpgme_error_t gpgme_op_edit       (gpgme_ctx_t ctx, gpgme_key_t key,
			           gpgme_edit_cb_t fnc, void *fnc_value,
			           gpgme_data_t out) _GPGME_DEPRECATED(1,7);
gpgme_error_t gpgme_op_card_edit_start (gpgme_ctx_t ctx, gpgme_key_t key,
					gpgme_edit_cb_t fnc, void *fnc_value,
					gpgme_data_t out)
                                        _GPGME_DEPRECATED(1,7);
gpgme_error_t gpgme_op_card_edit       (gpgme_ctx_t ctx, gpgme_key_t key,
				        gpgme_edit_cb_t fnc, void *fnc_value,
				        gpgme_data_t out)
                                        _GPGME_DEPRECATED(1,7);

/* The possible signature stati.  Deprecated, use error value in sig
 * status.  */
typedef enum
  {
    GPGME_SIG_STAT_NONE  = 0,
    GPGME_SIG_STAT_GOOD  = 1,
    GPGME_SIG_STAT_BAD   = 2,
    GPGME_SIG_STAT_NOKEY = 3,
    GPGME_SIG_STAT_NOSIG = 4,
    GPGME_SIG_STAT_ERROR = 5,
    GPGME_SIG_STAT_DIFF  = 6,
    GPGME_SIG_STAT_GOOD_EXP = 7,
    GPGME_SIG_STAT_GOOD_EXPKEY = 8
  }
_gpgme_sig_stat_t;
typedef _gpgme_sig_stat_t gpgme_sig_stat_t _GPGME_DEPRECATED(0,4);

/* The available key and signature attributes.  Deprecated, use the
 * individual result structures instead.  */
typedef enum
  {
    GPGME_ATTR_KEYID        = 1,
    GPGME_ATTR_FPR          = 2,
    GPGME_ATTR_ALGO         = 3,
    GPGME_ATTR_LEN          = 4,
    GPGME_ATTR_CREATED      = 5,
    GPGME_ATTR_EXPIRE       = 6,
    GPGME_ATTR_OTRUST       = 7,
    GPGME_ATTR_USERID       = 8,
    GPGME_ATTR_NAME         = 9,
    GPGME_ATTR_EMAIL        = 10,
    GPGME_ATTR_COMMENT      = 11,
    GPGME_ATTR_VALIDITY     = 12,
    GPGME_ATTR_LEVEL        = 13,
    GPGME_ATTR_TYPE         = 14,
    GPGME_ATTR_IS_SECRET    = 15,
    GPGME_ATTR_KEY_REVOKED  = 16,
    GPGME_ATTR_KEY_INVALID  = 17,
    GPGME_ATTR_UID_REVOKED  = 18,
    GPGME_ATTR_UID_INVALID  = 19,
    GPGME_ATTR_KEY_CAPS     = 20,
    GPGME_ATTR_CAN_ENCRYPT  = 21,
    GPGME_ATTR_CAN_SIGN     = 22,
    GPGME_ATTR_CAN_CERTIFY  = 23,
    GPGME_ATTR_KEY_EXPIRED  = 24,
    GPGME_ATTR_KEY_DISABLED = 25,
    GPGME_ATTR_SERIAL       = 26,
    GPGME_ATTR_ISSUER       = 27,
    GPGME_ATTR_CHAINID      = 28,
    GPGME_ATTR_SIG_STATUS   = 29,
    GPGME_ATTR_ERRTOK       = 30,
    GPGME_ATTR_SIG_SUMMARY  = 31,
    GPGME_ATTR_SIG_CLASS    = 32
  }
_gpgme_attr_t;
typedef _gpgme_attr_t gpgme_attr_t _GPGME_DEPRECATED(0,4);

/* Retrieve the signature status of signature IDX in CTX after a
 * successful verify operation in R_STAT (if non-null).  The creation
 * time stamp of the signature is returned in R_CREATED (if non-null).
 * The function returns a string containing the fingerprint.
 * Deprecated, use verify result directly.  */
const char *gpgme_get_sig_status (gpgme_ctx_t ctx, int idx,
                                  _gpgme_sig_stat_t *r_stat,
				  time_t *r_created) _GPGME_DEPRECATED(0,4);

/* Retrieve certain attributes of a signature.  IDX is the index
 * number of the signature after a successful verify operation.  WHAT
 * is an attribute where GPGME_ATTR_EXPIRE is probably the most useful
 * one.  WHATIDX is to be passed as 0 for most attributes . */
unsigned long gpgme_get_sig_ulong_attr (gpgme_ctx_t c, int idx,
                                        _gpgme_attr_t what, int whatidx)
     _GPGME_DEPRECATED(0,4);
const char *gpgme_get_sig_string_attr (gpgme_ctx_t c, int idx,
				       _gpgme_attr_t what, int whatidx)
     _GPGME_DEPRECATED(0,4);


/* Get the key used to create signature IDX in CTX and return it in
 * R_KEY.  */
gpgme_error_t gpgme_get_sig_key (gpgme_ctx_t ctx, int idx, gpgme_key_t *r_key)
     _GPGME_DEPRECATED(0,4);

/* Create a new data buffer which retrieves the data from the callback
 * function READ_CB.  Deprecated, please use gpgme_data_new_from_cbs
 * instead.  */
gpgme_error_t gpgme_data_new_with_read_cb (gpgme_data_t *r_dh,
					   int (*read_cb) (void*,char *,
							   size_t,size_t*),
					   void *read_cb_value)
     _GPGME_DEPRECATED(0,4);

/* Return the value of the attribute WHAT of KEY, which has to be
 * representable by a string.  IDX specifies the sub key or user ID
 * for attributes related to sub keys or user IDs.  Deprecated, use
 * key structure directly instead. */
const char *gpgme_key_get_string_attr (gpgme_key_t key, _gpgme_attr_t what,
				       const void *reserved, int idx)
     _GPGME_DEPRECATED(0,4);

/* Return the value of the attribute WHAT of KEY, which has to be
 * representable by an unsigned integer.  IDX specifies the sub key or
 * user ID for attributes related to sub keys or user IDs.
 * Deprecated, use key structure directly instead.  */
unsigned long gpgme_key_get_ulong_attr (gpgme_key_t key, _gpgme_attr_t what,
					const void *reserved, int idx)
     _GPGME_DEPRECATED(0,4);

/* Return the value of the attribute WHAT of a signature on user ID
 * UID_IDX in KEY, which has to be representable by a string.  IDX
 * specifies the signature.  Deprecated, use key structure directly
 * instead.  */
const char *gpgme_key_sig_get_string_attr (gpgme_key_t key, int uid_idx,
					   _gpgme_attr_t what,
					   const void *reserved, int idx)
     _GPGME_DEPRECATED(0,4);

/* Return the value of the attribute WHAT of a signature on user ID
 * UID_IDX in KEY, which has to be representable by an unsigned
 * integer string.  IDX specifies the signature.  Deprecated, use key
 * structure directly instead.  */
unsigned long gpgme_key_sig_get_ulong_attr (gpgme_key_t key, int uid_idx,
					    _gpgme_attr_t what,
					    const void *reserved, int idx)
     _GPGME_DEPRECATED(0,4);


gpgme_error_t gpgme_op_import_ext (gpgme_ctx_t ctx, gpgme_data_t keydata,
				   int *nr) _GPGME_DEPRECATED(0,4);

/* DO NOT USE.  */
void gpgme_trust_item_release (gpgme_trust_item_t item) _GPGME_DEPRECATED(0,4);

/* DO NOT USE.  */
const char *gpgme_trust_item_get_string_attr (gpgme_trust_item_t item,
					      _gpgme_attr_t what,
					      const void *reserved, int idx)
     _GPGME_DEPRECATED(0,4);

/* DO NOT USE.  */
int gpgme_trust_item_get_int_attr (gpgme_trust_item_t item, _gpgme_attr_t what,
				   const void *reserved, int idx)
     _GPGME_DEPRECATED(0,4);

/* Compat.
 * This structure shall be considered read-only and an application
 * must not allocate such a structure on its own.  */
struct _gpgme_op_assuan_result
{
  /* Deprecated.  Use the second value in a DONE event or the
     synchronous variant gpgme_op_assuan_transact_ext.  */
  gpgme_error_t err _GPGME_DEPRECATED_OUTSIDE_GPGME(1,2);
};
typedef struct _gpgme_op_assuan_result *gpgme_assuan_result_t;


/* Return the result of the last Assuan command. */
gpgme_assuan_result_t gpgme_op_assuan_result (gpgme_ctx_t ctx)
  _GPGME_DEPRECATED(1,2);

gpgme_error_t
gpgme_op_assuan_transact (gpgme_ctx_t ctx,
			      const char *command,
			      gpgme_assuan_data_cb_t data_cb,
			      void *data_cb_value,
			      gpgme_assuan_inquire_cb_t inq_cb,
			      void *inq_cb_value,
			      gpgme_assuan_status_cb_t status_cb,
                          void *status_cb_value) _GPGME_DEPRECATED(1,2);



typedef gpgme_ctx_t GpgmeCtx _GPGME_DEPRECATED(0,4);
typedef gpgme_data_t GpgmeData _GPGME_DEPRECATED(0,4);
typedef gpgme_error_t GpgmeError _GPGME_DEPRECATED(0,4);
typedef gpgme_data_encoding_t GpgmeDataEncoding _GPGME_DEPRECATED(0,4);
typedef gpgme_pubkey_algo_t GpgmePubKeyAlgo _GPGME_DEPRECATED(0,4);
typedef gpgme_hash_algo_t GpgmeHashAlgo _GPGME_DEPRECATED(0,4);
typedef gpgme_sig_stat_t GpgmeSigStat _GPGME_DEPRECATED(0,4);
typedef gpgme_sig_mode_t GpgmeSigMode _GPGME_DEPRECATED(0,4);
typedef gpgme_attr_t GpgmeAttr _GPGME_DEPRECATED(0,4);
typedef gpgme_validity_t GpgmeValidity _GPGME_DEPRECATED(0,4);
typedef gpgme_protocol_t GpgmeProtocol _GPGME_DEPRECATED(0,4);
typedef gpgme_engine_info_t GpgmeEngineInfo _GPGME_DEPRECATED(0,4);
typedef gpgme_subkey_t GpgmeSubkey _GPGME_DEPRECATED(0,4);
typedef gpgme_key_sig_t GpgmeKeySig _GPGME_DEPRECATED(0,4);
typedef gpgme_user_id_t GpgmeUserID _GPGME_DEPRECATED(0,4);
typedef gpgme_key_t GpgmeKey _GPGME_DEPRECATED(0,4);
typedef gpgme_passphrase_cb_t GpgmePassphraseCb _GPGME_DEPRECATED(0,4);
typedef gpgme_progress_cb_t GpgmeProgressCb _GPGME_DEPRECATED(0,4);
typedef gpgme_io_cb_t GpgmeIOCb _GPGME_DEPRECATED(0,4);
typedef gpgme_register_io_cb_t GpgmeRegisterIOCb _GPGME_DEPRECATED(0,4);
typedef gpgme_remove_io_cb_t GpgmeRemoveIOCb _GPGME_DEPRECATED(0,4);
typedef gpgme_event_io_t GpgmeEventIO _GPGME_DEPRECATED(0,4);
typedef gpgme_event_io_cb_t GpgmeEventIOCb _GPGME_DEPRECATED(0,4);
#define GpgmeIOCbs gpgme_io_cbs
typedef gpgme_data_read_cb_t GpgmeDataReadCb _GPGME_DEPRECATED(0,4);
typedef gpgme_data_write_cb_t GpgmeDataWriteCb _GPGME_DEPRECATED(0,4);
typedef gpgme_data_seek_cb_t GpgmeDataSeekCb _GPGME_DEPRECATED(0,4);
typedef gpgme_data_release_cb_t GpgmeDataReleaseCb _GPGME_DEPRECATED(0,4);
#define GpgmeDataCbs gpgme_data_cbs
typedef gpgme_encrypt_result_t GpgmeEncryptResult _GPGME_DEPRECATED(0,4);
typedef gpgme_sig_notation_t GpgmeSigNotation _GPGME_DEPRECATED(0,4);
typedef	gpgme_signature_t GpgmeSignature _GPGME_DEPRECATED(0,4);
typedef gpgme_verify_result_t GpgmeVerifyResult _GPGME_DEPRECATED(0,4);
typedef gpgme_import_status_t GpgmeImportStatus _GPGME_DEPRECATED(0,4);
typedef gpgme_import_result_t GpgmeImportResult _GPGME_DEPRECATED(0,4);
typedef gpgme_genkey_result_t GpgmeGenKeyResult _GPGME_DEPRECATED(0,4);
typedef	gpgme_trust_item_t GpgmeTrustItem _GPGME_DEPRECATED(0,4);
typedef gpgme_status_code_t GpgmeStatusCode _GPGME_DEPRECATED(0,4);

#ifdef __cplusplus
}
#endif
#endif /* GPGME_H */
/*
Local Variables:
buffer-read-only: t
End:
*/
