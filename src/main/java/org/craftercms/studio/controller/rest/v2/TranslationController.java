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

import org.craftercms.studio.api.v1.to.TranslationConfigTo;
import org.craftercms.studio.api.v2.service.translation.TranslationService;
import org.craftercms.studio.model.rest.ApiResponse;
import org.craftercms.studio.model.rest.ResponseBody;
import org.craftercms.studio.model.rest.ResultOne;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.craftercms.studio.controller.rest.v2.ResultConstants.RESULT_KEY_CONFIG;

/**
 * Rest controller to handle all translation related operations
 *
 * @author joseross
 * @since 3.2.0
 */
@RestController
@RequestMapping("/api/2/translation")
public class TranslationController {

    /**
     * The translation service
     */
    protected TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @GetMapping("/config")
    public ResponseBody getConfiguration(@RequestParam String siteId) {
        ResultOne<TranslationConfigTo> result = new ResultOne<>();
        result.setEntity(RESULT_KEY_CONFIG, translationService.getConfig(siteId));
        result.setResponse(ApiResponse.OK);

        ResponseBody body = new ResponseBody();
        body.setResult(result);

        return body;
    }

}
