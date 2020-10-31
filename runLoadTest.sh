#!/usr/bin/env bash

set -x

sbt "runMain com.reactivebank.accounts.LoadTest -Dcinnamon.prometheus.http-server.port=9004"
