#!/bin/bash
set -e

PORT="${PORT:-8080}"
PEER_ID="${PEER_ID:-DOCKER_NODE}"

# Ensure database directory exists
mkdir -p /app/database

case "$1" in
  web)
    echo "============================================================"
    echo "  Starting CampusMesh Web Dashboard on port ${PORT}..."
    echo "============================================================"
    exec java -cp "bin:lib/*" campusmesh.web.CampusMeshWebRunner "${PORT}" "${PEER_ID}" --public
    ;;
  demo)
    echo "============================================================"
    echo "  Running CampusMesh Multi-Transport DTN Demo Walkthrough..."
    echo "============================================================"
    exec java -cp "bin:lib/*" campusmesh.demo.MultiTransportMeshDemo
    ;;
  cli|interactive)
    exec java -cp "bin:lib/*" campusmesh.demo.InteractiveCLI
    ;;
  test)
    echo "============================================================"
    echo "  Running CampusMesh Automated Test Suites (52 tests)..."
    echo "============================================================"
    java -cp "bin:lib/*" campusmesh.test.CampusMeshTestSuite
    java -cp "bin:lib/*" campusmesh.test.HttpApiTestSuite
    java -cp "bin:lib/*" campusmesh.test.DiscoveryTestSuite
    java -cp "bin:lib/*" campusmesh.test.BleProtocolTestSuite
    java -cp "bin:lib/*" campusmesh.test.AndroidInteroperabilityTest
    java -cp "bin:lib/*" campusmesh.test.IOSInteroperabilityTest
    echo "============================================================"
    echo "  All 52 tests passed successfully!"
    echo "============================================================"
    ;;
  *)
    exec "$@"
    ;;
esac
