#!/bin/sh
if [ $# -eq 0 ]
then
  VERBE="start"
else
  VERBE=$1
fi
if [ "${VERBE}" = "start" ]
then
  rm %ROOT%/conf/dip_stop.txt
  echo Create %ROOT%/conf/dip_stop.txt to stop DIP Monitor
  java -cp %ROOT%/lib/r66/%WaarpVitam%:%VITAM% -Dlogback.configurationFile=%ROOT%/conf/r66/logback-client.xml -Dvitam.tmp.folder=%ROOT%/tmp/r66 -Dvitam.config.folder=%ROOT%/conf/r66/vitam -Dvitam.data.folder=%ROOT%/data/r66 -Dvitam.log.folder=%ROOT%/log/r66 org.waarp.vitam.dip.DipMonitor -e 10 -s %ROOT%/conf/r66/dip_stop.txt -w %ROOT%/conf/r66/config-clientSubmitA.xml
elif [ "${VERBE}" = "stop" ]
then
  echo DIP Monitor will stop
  touch %ROOT%/conf/dip_stop.txt
elif [ "${VERBE}" = "status" ]
then
  ps -aux | grep -v ps | grep DipMonitor
  if [ $? -eq 0 ]
  then
    echo DipMonitor is running
  else
    echo DipMonitor is not running
  fi
fi
