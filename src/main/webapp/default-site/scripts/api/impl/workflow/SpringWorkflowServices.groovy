
/*
 * Crafter Studio Web-content authoring solution
 * Copyright (C) 2007-2014 Crafter Software Corporation.
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
package scripts.api.impl.workflow

/**
 * @author Dejan Brkic
 */

class SpringWorkflowServices {

    def context = null

    /**
     * constructor
     *
     * @param context - service context
     */
    def SpringWorkflowServices(context) {
        this.context = context;
    }

    def getInProgressItems(site, sort, ascending, inProgressOnly) {
        def springBackedService = this.context.applicationContext.get("cstudioWorkflowService");
        springBackedService.getInProgressItems(site, sort, ascending, inProgressOnly);
    }

    def getGoLiveItems(site, sort, ascending) {
        def springBackedService = this.context.applicationContext.get("cstudioWorkflowService");
        springBackedService.getGoLiveItems(site, sort, ascending);
    }

    def getWorkflowAffectedPaths(site, path) {
        def springBackedService = this.context.applicationContext.get("cstudioWorkflowService");
        springBackedService.getWorkflowAffectedPaths(site, path);
    }

    def goDelete(site, requestBody, user) {
        def springBackedService = this.context.applicationContext.get("cstudioWorkflowService");
        springBackedService.goDelete(site, requestBody, user);
    }

    def goLive(site, requestBody) {
        def springBackedService = this.context.applicationContext.get("cstudioWorkflowService");
        springBackedService.goLive (site, requestBody);
    }

    def submitToGoLive(site, user, requestBody) {
        def springBackedService = this.context.applicationContext.get("cstudioWorkflowService");
        springBackedService.submitToGoLive (site, user, requestBody);
    }
}

