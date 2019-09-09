#!/bin/sh
echo Create %ROOT%/conf/ingest_stop.txt to stop Ingest Monitor
java -cp %ROOT%/bin/Waarp-Vitam-Ingest-3.2.0-jar-with-dependencies.jar -Dlogback.configurationFile=%ROOT%/conf/logback-client.xml -Dvitam.tmp.folder=%ROOT%/work -Dvitam.config.folder=%ROOT%/conf/vitam -Dvitam.data.folder=%ROOT%/data -Dvitam.log.folder=%ROOT%/log org.waarp.vitam.ingest.IngestMonitor -e 10 -s %ROOT%/conf/ingest_stop.txt -w %ROOT%/conf/config-clientSubmitA.xml
