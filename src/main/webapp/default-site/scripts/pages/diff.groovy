/*
 * Copyright (C) 2007-2019 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import scripts.libs.EnvironmentOverrides
import scripts.libs.HTMLCompareTools
import scripts.api.ContentServices
import org.apache.commons.lang3.StringEscapeUtils


def result = [:]
def site = request.getParameter("site")
def path = request.getParameter("path")
def version = request.getParameter("version")
def versionTO = request.getParameter("versionTO")
def escaped = request.getParameter("escaped")

def context = ContentServices.createContext(applicationContext, request)
String original = "UNSET"
String revised = "UNSET"

model.version = version
model.versionTO = versionTO

model.xsl = HTMLCompareTools.CONTENT_XML_TO_HTML_XSL

if([Collection, Object[]].any { it.isAssignableFrom(version.getClass()) } == false && !versionTO) {
	original = ContentServices.getContent(site, path, false, context)
	revised = ContentServices.getContentVersionAtPath(site, path, version, context)
}
else {
	original = ContentServices.getContentVersionAtPath(site, path, version, context)
	revised = ContentServices.getContentVersionAtPath(site, path, versionTO, context)
}

if(!escaped){
	model.variantA = HTMLCompareTools.xmlAsStringToHtml(revised)
	model.variantB = HTMLCompareTools.xmlAsStringToHtml(original)
}else{
	model.revisedEscaped = HTMLCompareTools.xmlEscapedFormatted(revised)
	model.variantA = '<?xml version="1.0" encoding="UTF-8"?><html><body>' + model.revisedEscaped + '</body></html>'
	model.originalEscaped = HTMLCompareTools.xmlEscapedFormatted(original)
	model.variantB = '<?xml version="1.0" encoding="UTF-8"?><html><body>' + model.originalEscaped + '</body></html>'
}

model.diff = HTMLCompareTools.diff(model.variantA, model.variantB)

model.dir = path

model.envConfig = EnvironmentOverrides.getValuesForSite(applicationContext, request, response)  
model.cookieDomain = request.getServerName()     



