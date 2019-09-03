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
 * DIP package contains all logic to apply a DIP send through Waarp
 * from Vitam export operation.<br>
 * <br>
 *
 * Export is a download from Vitam of a DIP (binary file in ZIP format) of
 * archives. This plugin allows to not use HTTP download but HTTP local
 * download command of Vitam, using Waarp to transfer the DIP.<br>
 * <br>
 * Waarp-Vitam Export plugin works as a Vitam Access client as follow:<br>
 * <br>
 * <ol>
 * <li>Ask for a DIP through Waarp through a post operation of a virtual send
 * of the request in Vitam DSL in a file.</li>
 * <li>Than the plugin will transfer the file as a request using the Vitam
 * Client.</li>
 * <li>Then it will ask as a pooling for the availability of the DIP and will
 * forward it when ready to the Waarp partner.</li>
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
package org.waarp.vitam.dip;

