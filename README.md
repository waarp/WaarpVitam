Waarp Vitam
=============

You will find in this web site the sub project Waarp Vitam.

The global license is GPL V3.

Waarp is a project that provides, among other packages, 
an open source massive file transfer monitor 
in Java. Its goal is to unify several protocols (FTP, SSH, HTTP and proprietary 
protocols) in an efficient and secured way. Its purpose is to enable bridging between 
several protocols and to enable dynamic pre or post action on transfer or other commands.

[Vitam](https://www.programmevitam.fr/) is a French government project related to digital archiving.


This project proposes an integration of Waarp and Vitam together.



*Important notice*
------------------
Vitam introduces a version V3 that has the following issues:
 * Java 11 is now mandatory, despite the large usage of Java 8 out of there. This prevents a lot if final users or co-project as this one to follow easily the path taken by Vitam team. Java 11 is not yet quite a success, considering a huge number of Java projects still using Java 8 as the minimal requirement.
 * Several issues were encountered in using Version 3 compares to Version 2.X (last being 2.15.3): while Java API are the same, the behaviors are not, those preventing an clean upgrade from V2 to V3. 

However we work with Vitam team to get a Waarp version available for both versions 2 and 3. Note that the "all jar" versions does not include any more the Vitam jar, in order to allow you to use either v2.15.3 or v3.0.1 and following versions.

You therefore need to ass the following jars with the Waarp-Vitam library:

**Common Jar for both V2 and V3 versions of Vitam**

*Vitam jars:*
 * fr.gouv.vitam:ingest-external-client
 * fr.gouv.vitam:common-public-client
 * fr.gouv.vitam:ingest-external-api
 * fr.gouv.vitam:access-external-client
 * fr.gouv.vitam:common-public
 * fr.gouv.vitam:common-http-interface
 * fr.gouv.vitam:access-external-api
 * fr.gouv.vitam:common-database-public

**Specific to V2 version of Vitam**

*Vitam jars:*
 * fr.gouv.vitam:access-external-common:jar:2.15.3
 * fr.gouv.vitam:logbook-common-client:jar:2.15.3
 * fr.gouv.vitam:logbook-common:jar:2.15.3

*Other jars:*
 * com.github.fge:json-schema-validator:jar:2.2.6
 * com.googlecode.libphonenumber:libphonenumber:jar:6.2
 * com.github.fge:json-schema-core:jar:1.2.5
 * com.github.fge:uri-template:jar:0.9
 * org.mozilla:rhino:jar:1.7R4
 * javax.mail:mailapi:jar:1.4.3
 * net.sf.jopt-simple:jopt-simple:jar:4.6


 
Support
-------

Support is available through community and also through commercial support
with the company named [Waarp](http://www.waarp.fr/)

![Waarp Company](http://waarp.github.com/Waarp/res/waarp/waarp.gif "Waarp")

 * Installation and parameters
 * Integration, additional development
 * Support, maintenance, phone support
 
