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

package org.craftercms.studio.controller.rest.v2;

/**
 * Keys used for the results in the Rest API.
 * @author joseross
 */
public interface ResultConstants {

    String RESULT_KEY_GROUP = "group";
    String RESULT_KEY_GROUPS = "groups";

    String RESULT_KEY_USER = "user";
    String RESULT_KEY_USERS = "users";
    String RESULT_KEY_CURRENT_USER = "authenticatedUser";
    String RESULT_KEY_LOGOUT_URL = "logoutUrl";

    String RESULT_KEY_SITES = "sites";

    String RESULT_KEY_ROLES = "roles";

    String RESULT_KEY_ITEM = "item";
    String RESULT_KEY_ITEMS = "items";
    String RESULT_KEY_MENU_ITEMS = "menuItems";

    String RESULT_KEY_CLUSTER_MEMBER = "clusterMember";
    String RESULT_KEY_CLUSTER_MEMBERS = "clusterMembers";

    String RESULT_KEY_ENVIRONMENT = "environment";

    String RESULT_KEY_BLUEPRINTS = "blueprints";

    String RESULT_KEY_RESULT = "result";

    /* Audit Controller */
    String RESULT_KEY_AUDIT_LOG = "auditLog";

    String RESULT_KEY_STAUS = "status";
    String RESULT_KEY_VERSION = "version";
    String RESULT_KEY_MEMORY = "memory";
    String RESULT_KEY_EVENTS = "events";

    /* Repository management controller */
    String RESULT_KEY_REMOTES = "remotes";
    String RESULT_KEY_REPOSITORY_STATUS = "repositoryStatus";
    String RESULT_KEY_DIFF = "diff";

    /* Dependency controller */
    String RESULT_KEY_SOFT_DEPENDENCIES = "softDependencies";
    String RESULT_KEY_HARD_DEPENDENCIES = "hardDependencies";

    /* Marketplace controller */
    String RESULT_KEY_PLUGINS = "plugins";

    /* Configuration controller */
    String RESULT_KEY_HISTORY = "history";

    /* Content controller */
    String RESULT_KEY_CHILD_ITEMS = "childItems";
    String RESULT_KEY_DEPENDENT_ITEMS = "dependentItems";

    /** Publish controller */
    String RESULT_KEY_PACKAGES = "packages";
    String RESULT_KEY_PACKAGE = "package";

    /* Translation controller */
    String RESULT_KEY_CONFIG = "config";

}
