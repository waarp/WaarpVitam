/*
 * This file is part of Waarp Project (named also Waarp or GG).
 *
 *  Copyright (c) 2019, Waarp SAS, and individual contributors by the @author
 *  tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 *  All Waarp Project is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 * Waarp . If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Ingest package contains all logic to apply from a Waarp reception of SIP
 * to Vitam ingest operation, and possibly the feedback operations, first
 * (mandatory) being the id of the ingest request, and second being the
 * pooling on ingest status and ATR from Vitam, leading to a forward of the
 * ATR to the partner as defined.<br>
 * If the second is not done, it is the responsability of the Waarp partner,
 * when it receives the id of the Ingest operation from Vitam through Waarp
 * to handle directly the handling of the final status and its ATR.<br>
 * <br>
 *
 * Ingest is an upload into Vitam of a SIP (binary file in ZIP format) of
 * archives. This plugin allows to not use HTTP upload but special HTTP local
 * upload command of Vitam, using Waarp to upload the SIP.<br>
 * <br>
 * Waarp-Vitam Ingest plugin works as a Vitam Ingest client as follow:<br>
 * <br>
 * <ol>
 * <li>After reception through Waarp, one post-operation will call this plugin
 * (through an ExecCommand or a JavaCommand) with extra informations as
 * needed. The command is the IngestTask main command (or constructor) to
 * allow the creation of the IngestRequest.</li>
 * <li>The plugin will use the following command from Vitam Ingest Client:<br>
 * <ul>
 * <li>RequestResponse<Void> ingestLocal(VitamContext vitamContext,LocalFile
 * localFile, String contextId, String action) throws
 * IngestExternalException;</li>
 * <li>Where arguments are:
 * <ul>
 * <li>vitamContext = Among various information needed or optional for Vitam as
 * the tenant Id, the Access Contract, the ApplicationSessionId, the
 * PersonalCertificate</li>
 * <li>localFile = the path to the local SIP file</li>
 * <li>contextId = context of the SIP, among DEFAULT_WORKFLOW, HOLDING_SCHEME,
 * FILING_SCHEME,BLANK_TEST</li>
 * <li>action = "RESUME" (no TEST mode will be supported such as "NEXT"</li>
 * </ul></li></ul></li>
 * <li>Check Ingest and feedback:<br>
 * <ul>
 * <li>Once the SIP is passed to Vitam, an ID of Ingest Operation is given back
 * to the Waarp Partner.</li>
 * <li>If the IngestRequest asks for it, the IngestMonitor will then pooling
 * the status of the Ingest to get back the ATR (ArchiveTransferReply) using
 * the following Vitam client command:<br>
 * <ul>
 * <li>Response downloadObjectAsync(VitamContext vitamContext,String objectId,
 * IngestCollection type) throws VitamClientException;</li></ul></li>
 * <li>When received, the ATR is sent back to the Waarp Partner and the
 * IngesrRequest is closed.</li>
 * </ul></li>
 * </ol>
 * <br>
 * Documentation:<br>
 * <ul>
 * <li>http://www.programmevitam.fr/ressources/DocCourante/html/manuel-integration/client-usage.html</li>
 * <li>http://www.programmevitam.fr/ressources/DocCourante/raml/externe/ingest.html</li>
 * <li>http://www.programmevitam.fr/ressources/DocCourante/javadoc/fr/gouv/vitam/ingest/external/client/IngestExternalClient.html</li>
 * <li>http://www.programmevitam.fr/ressources/DocCourante/html/archi/archi-applicative/20-services-list.html#api-externes-ingest-external-et-access-external</li>
 * <li>http://www.programmevitam.fr/ressources/DocCourante/html/archi/archi-exploit-infra/services/ingest-external.html</li>
 * <li>http://www.programmevitam.fr/ressources/DocCourante/html/archi/securite/00-principles.html#principes-de-securisation-des-acces-externes</li>
 * </ul>
 */
package org.waarp.vitam.ingest;

