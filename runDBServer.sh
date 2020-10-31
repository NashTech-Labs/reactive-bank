#!/usr/bin/env bash

set -x

sbt "runMain com.reactivebank.accounts.H2Database"
