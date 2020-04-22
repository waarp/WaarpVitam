#!/bin/sh
if [ $# -eq 0 ]
then
  VERBE="start"
else
  VERBE=$1
fi
if [ "${VERBE}" = "start" ]
then
  rm %ROOT%/conf/ingest_stop.txt
  echo Create %ROOT%/conf/ingest_stop.txt to stop Ingest Monitor
  java -cp %ROOT%/lib/r66/%WaarpVitam%:%VITAM% -Dlogback.configurationFile=%ROOT%/conf/r66/logback-client.xml -Dvitam.tmp.folder=%ROOT%/tmp/r66 -Dvitam.config.folder=%ROOT%/conf/r66/vitam -Dvitam.data.folder=%ROOT%/data/r66 -Dvitam.log.folder=%ROOT%/log/r66 org.waarp.vitam.ingest.IngestMonitor -e 10 -s %ROOT%/conf/r66/ingest_stop.txt -w %ROOT%/conf/r66/config-clientSubmitA.xml
elif [ "${VERBE}" = "stop" ]
then
  echo Ingest Monitor will stop
  touch %ROOT%/conf/ingest_stop.txt
elif [ "${VERBE}" = "status" ]
then
  ps -aux | grep -v ps | grep IngestMonitor
  if [ $? -eq 0 ]
  then
    echo IngestMonitor is running
  else
    echo IngestMonitor is not running
  fi
fi

