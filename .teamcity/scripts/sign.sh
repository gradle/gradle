#!/bin/bash

# Script to sign all zip and jar files in a directory with PGP keys
# Usage: ./sign.sh [directory]
# Environment variables required:
#   PGP_SIGNING_KEY - The PGP private key (armored)
#   PGP_SIGNING_KEY_PASSPHRASE - The passphrase for the key

set -e

# Get directory from argument or use current directory
DIR="${1:-.}"

# Check if directory exists
if [ ! -d "$DIR" ]; then
    echo "Error: Directory '$DIR' does not exist" >&2
    exit 1
fi

# Check for required environment variables
if [ -z "$PGP_SIGNING_KEY" ]; then
    echo "Error: PGP_SIGNING_KEY environment variable is not set" >&2
    exit 1
fi

if [ -z "$PGP_SIGNING_KEY_PASSPHRASE" ]; then
    echo "Error: PGP_SIGNING_KEY_PASSPHRASE environment variable is not set" >&2
    exit 1
fi

# Create temporary directory for GPG operations
TEMP_DIR=$(mktemp -d)
trap "rm -rf '$TEMP_DIR'" EXIT

# Setup GPG
export GNUPGHOME="$TEMP_DIR/.gnupg"
mkdir -p "$GNUPGHOME"
chmod 700 "$GNUPGHOME"

# Import the key
if ! echo "$PGP_SIGNING_KEY" | gpg --batch --import >/dev/null 2>&1; then
    echo "Error: Failed to import PGP key" >&2
    exit 1
fi

# Get the key ID
KEY_ID=$(gpg --list-secret-keys --keyid-format LONG 2>/dev/null | grep -E "^sec" | head -1 | awk '{print $2}' | cut -d'/' -f2)

if [ -z "$KEY_ID" ]; then
    echo "Error: Could not determine key ID from imported key" >&2
    exit 1
fi

echo "Using PGP key ID: $KEY_ID"
echo "Signing zip/jar files in: $DIR"
echo ""

# Find and sign all jar/zip files
SIGNED_COUNT=0
FAILED_COUNT=0

while IFS= read -r -d '' zipfile || [ -n "$zipfile" ]; do
    # Skip if .asc file already exists
    if [ -f "${zipfile}.asc" ]; then
        echo "Skipping $(basename "$zipfile") - signature already exists"
        continue
    fi

    echo "Signing: $(basename "$zipfile")"

    # Sign the file
    GPG_OUTPUT=$(echo "$PGP_SIGNING_KEY_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 --armor --detach-sign --default-key "$KEY_ID" --output "${zipfile}.asc" "$zipfile" 2>&1)
    GPG_EXIT_CODE=$?
    if [ $GPG_EXIT_CODE -eq 0 ]; then
        echo "  ✓ Created: $(basename "${zipfile}.asc")"
        SIGNED_COUNT=$((SIGNED_COUNT + 1))
    else
        echo "  ✗ Failed to sign: $(basename "$zipfile")" >&2
        echo "$GPG_OUTPUT" >&2
        FAILED_COUNT=$((FAILED_COUNT + 1))
    fi
done < <(find "$DIR" -maxdepth 1 -type f \( -name "*.zip" -o -name "*.jar" \) -print0)

echo ""
echo "Summary:"
echo "  Signed: $SIGNED_COUNT"
if [ $FAILED_COUNT -gt 0 ]; then
    echo "  Failed: $FAILED_COUNT" >&2
    exit 1
fi

