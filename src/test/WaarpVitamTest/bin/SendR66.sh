#!/bin/sh

java -cp %ROOT%/bin/Waarp-Vitam-Ingest-3.2.0-jar-with-dependencies.jar -Dlogback.configurationFile=%ROOT%/conf/logback-client.xml org.waarp.openr66.client.DirectTransfer %ROOT%/conf/config-serverA.xml -to hosta -rule $2 -file $1

