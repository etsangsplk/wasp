#!/bin/sh
set -e
source ~/.nvm/nvm.sh
nvm install 8
nvm use 8
npm version $GO_PIPELINE_LABEL
yarn install
yarn testWithCoverage
npm publish
