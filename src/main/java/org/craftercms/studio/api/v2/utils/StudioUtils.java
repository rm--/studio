/*
 * Copyright (C) 2007-2022 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.craftercms.studio.api.v2.utils;

import org.craftercms.commons.http.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.craftercms.studio.api.v1.constant.StudioConstants;

import javax.activation.MimetypesFileTypeMap;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.craftercms.studio.controller.rest.v2.RequestConstants.REQUEST_PARAM_SITEID;

public abstract class StudioUtils {

    private static final Logger logger = LoggerFactory.getLogger(StudioUtils.class);

    public static String getMimeType(String filename) {
        MimetypesFileTypeMap mimeMap = new MimetypesFileTypeMap();
        return mimeMap.getContentType(filename);
    }

    /**
     * Obtains the siteId from the current request, always fails if called out of a request context
     * @return the siteId
     */
    public static String getSiteId() {
        var context = RequestContext.getCurrent();
        if (context == null) {
            throw new IllegalStateException("There is no request to get the siteId");
        }

        var request = context.getRequest();
        var siteId = request.getParameter(REQUEST_PARAM_SITEID);
        if (isEmpty(siteId)) {
            throw new IllegalStateException("There is no parameter to get the siteId");
        }

        return siteId;
    }

    public static boolean matchesPatterns(String path, List<String> patterns) {
        if (patterns != null) {
            for (String pattern : patterns) {
                if (path.matches(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the top level folder where {@code path} is contained.
     * The result will be a value from the list defined at StudioConstants.TOP_LEVEL_FOLDERS, if found, null otherwise.
     *
     * @param path the content path
     * @return top level root path, if matched, or null otherwise
     */
    public static String getTopLevelFolder(final String path) {
        return StudioConstants.TOP_LEVEL_FOLDERS.stream()
                // Remove index file at the end of the path (to support /site/website/index.xml case) so we always get a folder path
                .map(folder -> folder.replaceFirst(StudioConstants.FILE_SEPARATOR + StudioConstants.INDEX_FILE + "$", ""))
                .filter(path::startsWith)
                .findFirst()
                .orElse(null);
    }
}
