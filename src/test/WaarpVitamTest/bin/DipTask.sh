#!/bin/sh

java -cp %ROOT%/bin/Waarp-Vitam-Ingest-3.2.0-jar-with-dependencies.jar -Dlogback.configurationFile=%ROOT%/conf/logback-client.xml -Dvitam.tmp.folder=%ROOT%/work -Dvitam.config.folder=%ROOT%/conf/vitam -Dvitam.data.folder=%ROOT%/data -Dvitam.log.folder=%ROOT%/log org.waarp.vitam.ingest.DipTask -o %ROOT%/conf/config-ingest.property -w %ROOT%/conf/config-clientSubmitA.xml -f $1
