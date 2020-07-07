/*
 * Copyright (C) 2007-2020 Crafter Software Corporation. All Rights Reserved.
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

package org.craftercms.studio.impl.v2.service.item.internal;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.ibatis.annotations.Param;
import org.craftercms.studio.api.v1.dal.SiteFeed;
import org.craftercms.studio.api.v1.dal.SiteFeedMapper;
import org.craftercms.studio.api.v2.dal.Item;
import org.craftercms.studio.api.v2.dal.ItemDAO;
import org.craftercms.studio.api.v2.dal.ItemState;
import org.craftercms.studio.api.v2.service.item.internal.ItemServiceInternal;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.craftercms.studio.api.v1.constant.StudioConstants.FILE_SEPARATOR;
import static org.craftercms.studio.api.v2.dal.QueryParameterNames.SITE_ID;

public class ItemServiceInternalImpl implements ItemServiceInternal {

    SiteFeedMapper siteFeedMapper;
    ItemDAO itemDao;

    public ItemServiceInternalImpl(SiteFeedMapper siteFeedMapper, ItemDAO itemDao) {
        this.siteFeedMapper = siteFeedMapper;
        this.itemDao = itemDao;
    }

    @Override
    public void upsertEntry(String siteId, Item item) {
        List<Item> items = new ArrayList<Item>();
        items.addAll(getAncestors(item));
        items.add(item);
        upsertEntries(siteId, items);
    }

    private List<Item> getAncestors(Item item) {
        List<Item> ancestors = new ArrayList<Item>();
        String itemPath = item.getPath();
        Path p = Paths.get(itemPath);
        List<Path> parts = new LinkedList<>();
        p.getParent().iterator().forEachRemaining(parts::add);
        Item i = Item.cloneItem(item);
        i.setPath("");
        if (CollectionUtils.isNotEmpty(parts)) {
            for (Path ancestor : parts) {
                i = Item.cloneItem(i);
                i.setPath(i.getPath() + FILE_SEPARATOR + ancestor.toString());
                i.setPreviewUrl(i.getPath());
                i.setSystemType("folder");
                ancestors.add(i);
            }
        } else {
            i.setPreviewUrl(i.getPath());
            i.setSystemType("folder");
            ancestors.add(i);
        }
        return ancestors;
    }

    @Override
    public void upsertEntries(String siteId, List<Item> items) {
        itemDao.upsertEntries(items);
    }

    @Override
    public void updateParentIds(String siteId, String rootPath) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(SITE_ID, siteId);
        SiteFeed siteFeed = siteFeedMapper.getSite(params);
        itemDao.updateParentIdForSite(siteFeed.getId(), rootPath);
    }

    @Override
    public Item getItem(long id) {
        return itemDao.getItemById(id);
    }

    @Override
    public Item getItem(String siteId, String path) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(SITE_ID, siteId);
        SiteFeed siteFeed = siteFeedMapper.getSite(params);
        return itemDao.getItemBySiteIdAndPath(siteFeed.getId(), path);
    }

    @Override
    public void deleteItem(long itemId) {
        itemDao.deleteById(itemId);
    }

    @Override
    public void deleteItem(String siteId, String path) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(SITE_ID, siteId);
        SiteFeed siteFeed = siteFeedMapper.getSite(params);
        itemDao.deleteBySiteAndPath(siteFeed.getId(), path);
    }

    @Override
    public void updateItem(Item item) {
        itemDao.updateItem(item);
    }

    @Override
    public void setSystemProcessing(String siteId, String path, boolean isSystemProcessing) {
        List<String> paths = new ArrayList<String>();
        paths.add(path);
        setSystemProcessingBulk(siteId, paths, isSystemProcessing);
    }

    @Override
    public void setSystemProcessingBulk(String siteId, List<String> paths, boolean isSystemProcessing) {
        if (isSystemProcessing) {
            setStatesBySiteAndPathBulk(siteId, paths, ItemState.SYSTEM_PROCESSING.value);
        } else {
            resetStatesBySiteAndPathBulk(siteId, paths, ItemState.SYSTEM_PROCESSING.value);
        }
    }

    private void setStatesBySiteAndPathBulk(String siteId, List<String> paths, long statesBitMap) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(SITE_ID, siteId);
        SiteFeed siteFeed = siteFeedMapper.getSite(params);
        itemDao.setStatesBySiteAndPathBulk(siteFeed.getId(), paths, statesBitMap);
    }

    private void setStatesByIdBulk(List<Long> itemIds, long statesBitMap) {
        itemDao.setStatesByIdBulk(itemIds, statesBitMap);
    }

    private void resetStatesBySiteAndPathBulk(String siteId, List<String> paths, long statesBitMap) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(SITE_ID, siteId);
        SiteFeed siteFeed = siteFeedMapper.getSite(params);
        itemDao.resetStatesBySiteAndPathBulk(siteFeed.getId(), paths, statesBitMap);
    }

    private void resetStatesByIdBulk(List<Long> itemIds, long statesBitMap) {
        itemDao.resetStatesByIdBulk(itemIds, statesBitMap);
    }

    @Override
    public void setStateBits(String siteId, String path, long statesBitMask) {
        List<String> paths = new ArrayList<String>();
        paths.add(path);
        setStatesBySiteAndPathBulk(siteId, paths, statesBitMask);
    }

    @Override
    public void resetStateBits(String siteId, String path, long statesBitMask) {
        List<String> paths = new ArrayList<String>();
        paths.add(path);
        resetStatesBySiteAndPathBulk(siteId, paths, statesBitMask);
    }

    @Override
    public void setStateBits(long itemId, long statesBitMask) {
        List<Long> ids = new ArrayList<Long>();
        ids.add(itemId);
        setStatesByIdBulk(ids, statesBitMask);
    }

    @Override
    public void resetStateBits(long itemId, long statesBitMask) {
        List<Long> ids = new ArrayList<Long>();
        ids.add(itemId);
        resetStatesByIdBulk(ids, statesBitMask);
    }

    @Override
    public void updateStateBits(String siteId, String path, long onStateBitMap, long offStateBitMap) {
        List<String> paths = new ArrayList<String>();
        paths.add(path);
        updateStatesBySiteAndPathBulk(siteId, paths, onStateBitMap, offStateBitMap);
    }

    @Override
    public void updateStateBits(long itemId, long onStateBitMap, long offStateBitMap) {
        List<Long> ids = new ArrayList<Long>();
        ids.add(itemId);
        updateStateBitsBulk(ids, onStateBitMap, offStateBitMap);
    }

    @Override
    public void updateStateBitsBulk(String siteId, List<String> paths, long onStateBitMap, long offStateBitMap) {
        updateStatesBySiteAndPathBulk(siteId, paths, onStateBitMap, offStateBitMap);
    }

    @Override
    public void updateStateBitsBulk(List<Long> itemIds, long onStateBitMap, long offStateBitMap) {
        itemDao.updateStatesByIdBulk(itemIds, onStateBitMap, offStateBitMap);
    }

    private void updateStatesBySiteAndPathBulk(String siteId, List<String> paths, long onStateBitMap,
                                               long offStateBitMap) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(SITE_ID, siteId);
        SiteFeed siteFeed = siteFeedMapper.getSite(params);
        itemDao.updateStatesBySiteAndPathBulk(siteFeed.getId(), paths, onStateBitMap, offStateBitMap);
    }
}
