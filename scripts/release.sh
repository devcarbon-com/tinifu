#!/bin/bash
# Cut a release: tag vX.Y.Z and push it. The GitHub Actions release workflow
# (.github/workflows/release.yml) builds the binary on the tag, packages it,
# and publishes the release.
set -euo pipefail

VERSION="${1:?usage: scripts/release.sh v1.2.3}"
cd "$(dirname "$0")/.."

git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null 2>&1 && {
  echo "tag $VERSION already exists" >&2; exit 1; }

git tag "$VERSION"
git push origin "$VERSION"
echo "tag $VERSION pushed — CI is building the release:"
echo "  https://github.com/devcarbon-com/tinifu/actions"
