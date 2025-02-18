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
package org.craftercms.studio.api.v2.repository.blob;

import org.craftercms.commons.file.blob.Blob;
import org.craftercms.commons.file.blob.BlobStore;
import org.craftercms.studio.api.v1.repository.ContentRepository;

/**
 * Extension of {@link BlobStore} that adds support for Studio content repository operations
 *
 * @author joseross
 * @since 3.1.6
 */
public interface StudioBlobStore extends BlobStore, ContentRepository,
        org.craftercms.studio.api.v2.repository.ContentRepository {

    /**
     * Return a reference to a file in the store
     * @param path the path of the file
     * @return the blob object
     */
    Blob getReference(String path);

}
