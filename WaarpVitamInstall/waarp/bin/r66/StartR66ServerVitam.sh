#!/bin/sh
if [ $# -eq 0 ]
then
  VERBE="start"
else
  VERBE=$1
fi
if [ "${VERBE}" = "start" ]
then
  java -cp %ROOT%/lib/r66/%WaarpVitam% -Dlogback.configurationFile=%ROOT%/conf/r66/logback.xml -Dvitam.tmp.folder=%ROOT%/tmp/r66 -Dvitam.config.folder=%ROOT%/conf/r66/vitam -Dvitam.data.folder=%ROOT%/data/r66 -Dvitam.log.folder=%ROOT%/log/r66 org.waarp.openr66.server.R66Server %ROOT%/conf/r66/config-serverA.xml
elif [ "${VERBE}" = "stop" ]
then
  echo R66Server will stop
  CHECK=$(ps -aux | grep -v ps | grep R66Server)
  processid=$(echo "${CHECK}" | awk '{print $2}')
  kill -15 "${processid}"
elif [ "${VERBE}" = "status" ]
then
  ps -aux | grep -v ps | grep R66Server
  if [ $? -eq 0 ]
  then
    echo R66Server is running
  else
    echo R66Server is not running
  fi
fi
