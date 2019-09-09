#!/bin/sh

java -cp %ROOT%/bin/Waarp-Vitam-Ingest-3.2.0-jar-with-dependencies.jar -Dlogback.configurationFile=%ROOT%/conf/logback.xml -Dvitam.tmp.folder=%ROOT%/work -Dvitam.config.folder=%ROOT%/conf/vitam -Dvitam.data.folder=%ROOT%/data -Dvitam.log.folder=%ROOT%/log org.waarp.openr66.server.R66Server %ROOT%/conf/config-serverA.xml
