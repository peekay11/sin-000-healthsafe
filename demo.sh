#!/usr/bin/env bash
# HealthSafe demo launcher: starts the broker and all five services, then walks through the demo.
#   ./demo.sh        start broker + open one terminal window with a tab per service
#   ./demo.sh stop   stop every service and the broker

ROOT="$(dirname "$(readlink -f "$0")")"
SELF="$(readlink -f "$0")"
SERVICES=(ingestion-service alert-level-service staffing-service ward-service)

wait_for_health() {
  until curl -s "localhost:$1/health" >/dev/null 2>&1; do sleep 1; done
}

case "$1" in

  svc)
    # one service in its own tab
    cd "$ROOT/$2" && java -jar "target/$2.jar"
    exec bash
    ;;

  equipment)
    # held back so the queued alerts can be shown arriving
    cd "$ROOT/equipment-alert-service"
    echo "ward-service has already sent 4 equipment failures to equipment-failure-queue."
    echo "Nothing is listening yet, so they are waiting on the queue."
    echo
    read -rp "Press Enter to start equipment-alert-service... "
    java -jar target/equipment-alert-service.jar
    exec bash
    ;;

  walkthrough)
    # step-by-step demo: each command is shown, then runs when you press Enter
    echo "Waiting for services to start..."
    for port in 7030 7031 7032 7033; do wait_for_health "$port"; done
    clear
    step() {
      echo
      echo "------------------------------------------------------------"
      echo "# $1"
      echo "\$ $2"
      read -rp "[Enter to run] "
      eval "$2"
      echo
    }
    step "Cleaned ward data from ingestion-service" \
         "curl -s localhost:7030/wards | jq"
    step "ward-service fetching one ward from ingestion over HTTP" \
         "curl -s localhost:7031/wards/W-01 | jq"
    step "Departments" \
         "curl -s localhost:7031/departments | jq"
    step "Alert level 9 is rejected (valid range is 0-8)" \
         "curl -s -X PUT localhost:7032/alert-level -H 'Content-Type: application/json' -d '{\"level\":9}'"
    step "Alert level 3 is accepted" \
         "curl -s -X PUT localhost:7032/alert-level -H 'Content-Type: application/json' -d '{\"level\":3}'"
    step "Staffing schedule: calls ward-service + alert-level-service, then publishes to the topic" \
         "curl -s localhost:7033/staffing/W-01 | jq"
    step "Same schedule received by ward-service through staffing-events-topic" \
         "curl -s localhost:7031/staffing-updates | jq"
    step "Error: ward with no beds" \
         "curl -s localhost:7033/staffing/W-02"
    step "Error: ward that doesn't exist" \
         "curl -s localhost:7033/staffing/W-99"
    echo
    echo ">>> Now switch to the 'equipment' tab and press Enter to start it."
    echo ">>> The 4 queued alerts will arrive. Then come back here."
    step "Send a live equipment failure through equipment-failure-queue" \
         "curl -s -X POST localhost:7031/equipment-failure -H 'Content-Type: application/json' -d '{\"wardId\":\"W-06\",\"equipment\":\"MRI Scanner MRI-01\",\"severity\":\"CRITICAL\",\"time\":\"2026-11-12T18:00:00Z\"}'"
    echo
    echo "Demo finished. To shut everything down run: $SELF stop"
    exec bash
    ;;

  stop)
    pkill -f 'target/[a-z-]*-service\.jar'
    cd "$ROOT/common" && docker compose down
    echo "All services and the broker are stopped."
    ;;

  *)
    # build any missing jars
    for s in "${SERVICES[@]}" equipment-alert-service; do
      if [ ! -f "$ROOT/$s/target/$s.jar" ]; then
        echo "Building $s..."
        (cd "$ROOT/$s" && mvn -q -DskipTests package) || exit 1
      fi
    done

    # stop anything left over from an earlier run, so every port is free
    pkill -f 'target/[a-z-]*-service\.jar' && sleep 2

    # fresh broker, so the queue starts empty
    echo "Starting ActiveMQ..."
    (cd "$ROOT/common" && docker compose down >/dev/null 2>&1; docker compose up -d) || exit 1
    echo "Waiting for ActiveMQ to be ready..."
    until [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:8161)" != "000" ]; do sleep 1; done
    sleep 2

    args=()
    for s in "${SERVICES[@]}"; do
      args+=(--tab --title="${s%-service}" --command="$SELF svc $s")
    done
    gnome-terminal --window --title=broker --command="bash -c 'cd $ROOT/common && docker compose logs -f'" \
      "${args[@]}" \
      --tab --title=equipment --command="$SELF equipment" \
      --tab --title=demo --command="$SELF walkthrough" >/dev/null 2>&1 &
    disown
    echo "Opened the HealthSafe terminal window."
    ;;
esac
