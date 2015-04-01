/*******************************************************************************
 * Crafter Studio Web-content authoring solution
 *     Copyright (C) 2007-2013 Crafter Software Corporation.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.craftercms.studio.impl.v1.repository.alfresco;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.methods.multipart.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.String;
import java.util.*;
import java.net.*;
import java.net.URI;
import java.util.HashMap;
import java.util.regex.Matcher;
import javax.servlet.http.*;
import javax.servlet.http.Cookie;

import org.apache.commons.httpclient.*;

import net.sf.json.*;

import org.apache.commons.io.IOUtils;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.craftercms.commons.http.*;
import org.craftercms.studio.api.v1.constant.DmConstants;
import org.craftercms.studio.api.v1.ebus.EBusConstants;
import org.craftercms.studio.api.v1.ebus.RepositoryEventMessage;
import org.craftercms.studio.api.v1.exception.ContentNotFoundException;
import org.craftercms.studio.api.v1.job.CronJobContext;
import org.craftercms.studio.api.v1.log.Logger;
import org.craftercms.studio.api.v1.log.LoggerFactory;
import org.craftercms.studio.api.v1.repository.RepositoryItem;
import org.craftercms.studio.api.v1.service.security.SecurityProvider;
import org.craftercms.studio.api.v1.to.VersionTO;
import org.craftercms.studio.impl.v1.repository.AbstractContentRepository;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import reactor.core.Reactor;
import reactor.event.Event;


/* these go away */
import org.craftercms.studio.api.v1.service.deployment.CopyToEnvironmentItem;
import org.craftercms.studio.api.v1.service.fsm.TransitionEvent;
import org.craftercms.studio.api.v1.service.deployment.DeploymentException;
import org.craftercms.studio.api.v1.to.DeploymentEndpointConfigTO;
import javax.transaction.*;

/**
 * Alfresco repository implementation.  This is the only point of contact with Alfresco's API in
 * the entire system under the org.craftercms.cstudio.impl package structure
 * @author russdanner
 *
 */
public class AlfrescoContentRepository extends AbstractContentRepository 
implements SecurityProvider {

    private static final Logger logger = LoggerFactory.getLogger(AlfrescoContentRepository.class);

    @Override
    public InputStream getContent(String path) throws ContentNotFoundException {
        return getContentStreamCMIS(path);
    }

    @Override
    public boolean contentExists(String path) {
       return (this.getNodeRefForPathCMIS(path) != null);
    }


    @Override
    public boolean writeContent(String path, InputStream content) {
        logger.debug("writing content to " + path);
        addDebugStack();
        String uploadURI = "/api/upload";
        String contentType = "cm:content";
        int splitIndex = path.lastIndexOf("/");
        String name = path.substring(splitIndex + 1);
        // find the existing node by path
        String nodeRef = getNodeRefForPathCMIS(path);
        // find the target folder node by its path
        String folderPath = path.substring(0, splitIndex);
        String folderRef = getNodeRefForPathCMIS(folderPath);
        if (folderRef == null) {
            // if not, create the folder first
            int folderSplitIndex = folderPath.lastIndexOf("/");
            String parentFolderPath = folderPath.substring(0, folderSplitIndex);
            String folderName = folderPath.substring(folderSplitIndex + 1);
            folderRef = this.createFolderInternal(parentFolderPath, folderName);
        }
        // TODO: might still need to check if the folderRef still exists

        // add parameters
        Map<String, String> params = new HashMap<String, String>();
        params.put("filename", name);
        // if it's a new content, check if the folder exists
        params.put("destination", folderRef);
        if (nodeRef != null) {
            params.put("updateNodeRef", nodeRef);
        }
        //params.put("uploaddirectory", folderRef);
        // TODO: add description for version update - do we need this?
        params.put("contenttype", contentType);
        params.put("majorversion", "false");
        params.put("overwrite", "true");
        // read back nodeRef
        logger.debug("request params\n" + params);

        try {
            String response = this.alfrescoMultipartPostRequest(uploadURI, params, content, "application/xml", "UTF-8");
            RepositoryEventMessage message = new RepositoryEventMessage();
            Matcher m = DmConstants.DM_MULTI_REPO_PATH_PATTERN.matcher(path);
            if (m.matches()) {
                String site = m.group(3).length() != 0 ? m.group(3) : "";
                String relativePath = m.group(5).length() != 0 ? m.group(5) : "/";
                message.setSite(site);
                message.setPath(relativePath);
                message.setRequestContext(RequestContext.getCurrent());
                repositoryReactor.notify(EBusConstants.REPOSITORY_UPDATE_EVENT, Event.wrap(message));
            }
            logger.debug("done writing content to " + path);
            logger.debug("response back from the server: " + response);
            return true;
        } catch (Exception e) {
            logger.error("error writing to " + path, e);
            return false;
        } finally {
            IOUtils.closeQuietly(content);
        }
    }

    @Override
    public boolean createFolder(String path, String name) {
        addDebugStack();
        String folderRef = this.createFolderInternal(path, name);
        return folderRef != null;
    }

    @Override
    public boolean deleteContent(String path) {
        logger.debug("deleting content at " + path);
        addDebugStack();
        boolean result = false;
        String deleteURI = "/slingshot/doclib/action/file/node/";
        String nodeRef = getNodeRefForPathCMIS(path);

        if (nodeRef == null) {
            // if no nodeRef, it's already deleted so just return true
            logger.debug("No content found at " + path);
            result = true;
        } else {
            Map<String, String> params = new HashMap<String, String>();
            try {
                deleteURI = deleteURI + nodeRef.replace("://", "/");
                result = this.alfrescoDeleteRequest(deleteURI, params);
            } catch (Exception e) {
                logger.error("Error while deleting " + path, e);
            }
        }
        return result;
    }

    @Override
    public boolean copyContent(String fromPath, String toPath) {
        addDebugStack();
        return this.copyContentInternal(fromPath, toPath, false);
    }


    @Override
    public boolean moveContent(String fromPath, String toPath) {
        addDebugStack();
        return this.copyContentInternal(fromPath, toPath, true);
    };


    /**
     * get immediate children for path
     * @param path path to content
     */
    public RepositoryItem[] getContentChildren(String path) {

        addDebugStack();
        RepositoryItem[] items = getContentChildrenCMIS(path);
        return items;
        /*
        RepositoryItem[] items = new RepositoryItem[0];

        String cleanPath = path.replaceAll("//", "/"); // sometimes sent bad paths
               cleanPath = cleanPath.replace("/index.xml", "");
               cleanPath = (cleanPath.endsWith("/")) ? cleanPath.substring(0, cleanPath.length()-1) : cleanPath;


        Map<String, String> params = new HashMap<String, String>();
        String namespacedPath = cleanPath.replaceAll("/", "/cm:");
        String query = "PATH:\"/app:company_home" + namespacedPath + "/*" +  "\"";
        String nodeRef = getNodeRefForPathCMIS(path);
        int idx = nodeRef.lastIndexOf("/");
        nodeRef = nodeRef.substring(idx+1);

        String lookupNodeRefURI = "/slingshot/doclib/doclist/all/node/{storeType}/{storeId}/{nodeRef}";
        params.put("storeType", "workspace");
        params.put("storeId", "SpacesStore");
        params.put("nodeRef", nodeRef);

        try{
            InputStream responseStream = this.alfrescoGetRequest(lookupNodeRefURI, params);
            String jsonResponse = IOUtils.toString(responseStream, "utf-8");

            JsonConfig cfg = new JsonConfig();
            JSONObject root = JSONObject.fromObject(jsonResponse, cfg);
            int resultCount = root.getInt("totalRecords");
            JSONArray results = root.getJSONArray("items");

            items = new RepositoryItem[resultCount];
            for(int i=0; i<resultCount; i++) {
                JSONObject result = results.getJSONObject(i);
                String resultName = result.getString("fileName");
                boolean isFolder = result.getBoolean("isFolder");
                RepositoryItem item = new RepositoryItem();

                item.path = path;
                item.name = resultName;//.getString("prefixedName").replace("cm:", "");
                item.isFolder = isFolder;

                items[i] = item;
            }

        }
        catch(Exception err) {
            logger.error("error getting children for path (" + path + "): ", err);   
        }

        return items;
        */
    }

    /** 
     * get the version history for an item
     * @param path - the path of the item
     */
    public VersionTO[] getContentVersionHistory(String path) {
        addDebugStack();
        VersionTO[] versions = new VersionTO[0];

        try {
            String nodeRef = getNodeRefForPathCMIS(path);

            if(nodeRef != null) {
                // construct and execute url to download result
                String historyURI = "/api/version?nodeRef={nodeRef}";
                Map<String, String> params = new HashMap<String, String>();
                params.put("nodeRef", nodeRef);

                InputStream response = this.alfrescoGetRequest(historyURI, params);

                try{
                    String jsonResponse = IOUtils.toString(response, "utf-8");
                    JsonConfig cfg = new JsonConfig();
                    JSONArray results = JSONArray.fromObject(jsonResponse, cfg);
                    int resultCount = results.size();

                    if(resultCount > 0) {
                        versions = new VersionTO[resultCount];
                        for(int i=0; i<resultCount; i++) {
                            JSONObject result = results.getJSONObject(i);
                            JSONObject creator = result.getJSONObject("creator");
                            VersionTO version = new VersionTO();

                            version.setVersionNumber(result.getString("label"));
                            version.setLastModifier(creator.getString("userName"));
                            version.setLastModifiedDate(new Date()); //result.getString("createdDate"));
                            //       "createdDate": "16 Nov 2014 21:28:09 GMT-0500 (EST)",
                            //       "createdDateISO": "2014-11-16T21:28:09.141-05:00",
                            version.setComment(result.getString("description"));

                            versions[i] = version;
                        }
                    }
                }
                catch(Exception err) {
                    logger.error("err getting noderef for path (" + path + "): ", err);   
                }
            }
            else {
                throw new Exception("nodeRef not found for path: [" + path + "]");
            }
        }
        catch(Exception err) {
            logger.error("err getting content: ", err);   
        }

        return versions;
    }

    /** 
     * revert a version (create a new version based on an old version)
     * @param path - the path of the item to "revert"
     * @param version - old version ID to base to version on
     */
    public boolean revertContent(String path, String version, boolean major, String comment) {
        addDebugStack();
        boolean success = false;
        String nodeRef = getNodeRefForPathCMIS(path);
        
        if(nodeRef != null) {
            String revertCommand = "{" +
               "\"nodeRef\":\"" + nodeRef + "\"," + 
               "\"version\":\"" + version + "\"," +
               "\"majorVersion\":\"" + major + "\"," +
               "\"description\":\"" + comment+ "\"" +
               "}";

            try {
                InputStream bodyStream = IOUtils.toInputStream(revertCommand, "UTF-8");
                String result = alfrescoPostRequest("/api/revert", null, bodyStream, "application/json");
                
                success = result.contains("true");
             }
            catch(Exception err) {
                logger.error("err reverting content content: ", err);   
            }
        }

        return success;
    }

    /**
     * create a folder at the given path
     *
     * @param path
     *          a path to create a folder in
     * @param name
     *          a folder name to create
     * @return a node reference string of the new folder
     */
    protected String createFolderInternal(String path, String name) {
        logger.debug("creating a folder at " + path + " with name: " + name);
        String newFolderRef = null;
        String createFolderURI = "/api/type/cm%3afolder/formprocessor";
        Map<String, String> params = new HashMap<String, String>();
        String folderRef = getNodeRefForPathCMIS(path);
        if (StringUtils.isEmpty(folderRef)) {
            logger.error("Failed to create " + name + " folder since " + path + " does not exist.");
        } else {
            JSONObject requestObj = new JSONObject();
            requestObj.put("alf_destination", folderRef);
            requestObj.put("prop_cm_name", name);
            requestObj.put("prop_cm_title", name);
            InputStream is = IOUtils.toInputStream(requestObj.toString());
            try {
                String responseBody = this.alfrescoPostRequest(createFolderURI, params, is, "application/json");
                JsonConfig cfg = new JsonConfig();
                JSONObject respObj = JSONObject.fromObject(responseBody, cfg);
                newFolderRef = (String) respObj.get("persistedObject");
            } catch (Exception e) {
                logger.error("Error while creating " + name + " at " + path, e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
        return newFolderRef;
    }

    /**
     * copy content from pathA to pathB
     * @param fromPath
     *             the source path
     * @param toPath
     *              the target path
     * @param isCut
     *              true for move
     * @return  true if successful
     */
    protected boolean copyContentInternal(String fromPath, String toPath, boolean isCut) {
        logger.debug( (isCut ? "Move" : "Copy") + " content from " + fromPath + " to " + toPath);
        boolean result = false;
        // find all nodeRefs required
        String targetRef = getNodeRefForPathCMIS(toPath);
        String sourceRef = getNodeRefForPathCMIS(fromPath);
        // TODO: need to take care of duplicate at the target location
        if (targetRef != null && sourceRef != null) {
            String sourceParentRef = getNodeRefForPathCMIS(fromPath.substring(0, fromPath.lastIndexOf("/")));
            String copyURL = "/slingshot/doclib/action/copy-to/node/";
            String moveURL = "/slingshot/doclib/action/move-to/node/";
            String actionURL = (isCut) ? moveURL : copyURL;
            actionURL = actionURL + targetRef.replace("://", "/");
            // no parameter
            Map<String, String> params = new HashMap<String, String>();
            // create request body
            JSONObject requestObj = new JSONObject();
            String [] nodeRefs = new String[1];
            nodeRefs[0] = sourceRef;
            requestObj.put("nodeRefs", nodeRefs);
            requestObj.put("parentId", sourceParentRef);
            InputStream is = IOUtils.toInputStream(requestObj.toString());
            try {
                this.alfrescoPostRequest(actionURL, params, is, "application/json");
                return true;
            } catch (Exception e) {
                logger.error("Error while " + (isCut ? "moving" : "copying") + " content from " + fromPath + " to " + toPath, e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        } else {
            if (sourceRef == null) {
                logger.error((isCut ? "Move" : "Copy") + " failed since source path " + fromPath + " does not exist.");
            }
            if (targetRef == null) {
                logger.error((isCut ? "Move" : "Copy") + " failed since target path " + toPath + " does not exist.");
            }
        }
        return result;
    }


    /**
     * fire GET request to Alfresco with proper security
     */
    protected InputStream alfrescoGetRequest(String uri, Map<String, String> params) throws Exception {
        InputStream retResponse = null;

        URI serviceURI = new URI(buildAlfrescoRequestURL(uri, params));        

        retResponse = serviceURI.toURL().openStream();

        return retResponse;
    }

    /**
     * fire GET request to Alfresco with proper security
     */
    protected InputStream alfrescoGetHttpRequest(String uri, Map<String, String> params) throws Exception {
        InputStream retResponse = null;

        URI serviceURI = new URI(buildAlfrescoRequestURL(uri, params));

        //retResponse = serviceURI.toURL().openStream();

        HttpClient httpClient = new HttpClient(new MultiThreadedHttpConnectionManager());
        GetMethod getMethod = new GetMethod(serviceURI.toURL().toString());
        int status = httpClient.executeMethod(getMethod);
        if (status == 200) {
            retResponse = new ByteArrayInputStream(getMethod.getResponseBody());
        } else {
            // TODO: we might need to return response stream
            logger.error("Get request failed with " + status);
            return null;
        }
        return retResponse;
    }

    /**
     * fire DELETE request to Alfresco with proper security
     */
    protected boolean alfrescoDeleteRequest(String uri, Map<String, String> params) throws Exception {
        String serviceURL = buildAlfrescoRequestURL(uri, params);
        DeleteMethod deleteMethod = new DeleteMethod(serviceURL);
        HttpClient httpClient = new HttpClient(new MultiThreadedHttpConnectionManager());
        int status = httpClient.executeMethod(deleteMethod);
        if (status == 200 || status == 204) {
            return true;
        } else {
            // TODO: we might need to return response stream
            logger.error("Delete failed with " + status);
            return false;
        }
    }

    /**
     * fire POST request to Alfresco with propert security
     */
    protected String alfrescoPostRequest(String uri, Map<String, String> params, InputStream body, String bodyMimeType) throws Exception {
        String serviceURL = buildAlfrescoRequestURL(uri, params);        
        PostMethod postMethod = new PostMethod(serviceURL);
        postMethod.setRequestEntity(new InputStreamRequestEntity(body, bodyMimeType));

        HttpClient httpClient = new HttpClient(new MultiThreadedHttpConnectionManager());
        int status = httpClient.executeMethod(postMethod);
        
        return postMethod.getResponseBodyAsString();
    }

    /**
     * create a multipart post request and fire to Alfresco
     *
     * @param uri
     *          the target service URI
     * @param params
     *          request parameters
     * @param body
     *          post data
     * @param bodyMimeType
     *          post data mime type
     * @param charSet
     *          post data char set
     * @return response body
     * @throws Exception
     */
    protected String alfrescoMultipartPostRequest(String uri, Map<String, String> params, InputStream body, String bodyMimeType, String charSet) throws Exception {
        String serviceURL = buildAlfrescoRequestURL(uri, new HashMap<String, String>(0));
        PostMethod postMethod = new PostMethod(serviceURL);
        // create multipart request parts
        int partSize = params.size() + 1;
        Part[] parts = new Part[partSize];
        int index = 0;
        for (String key : params.keySet()) {
            parts[index] = new StringPart(key, params.get(key));
            index++;
        }
        byte[] bytes = IOUtils.toByteArray(body);
        String name = params.get("filename");
        PartSource partSource = new ByteArrayPartSource(name, bytes);
        parts[index] = new FilePart("filedata", partSource, bodyMimeType, charSet);

        postMethod.setRequestEntity(new MultipartRequestEntity(parts, postMethod.getParams()));

        // connect to alfresco and get response
        HttpClient httpClient = new HttpClient(new MultiThreadedHttpConnectionManager());
        logger.debug("Executing multipart post request to " + uri);
        int status = httpClient.executeMethod(postMethod);
        logger.debug("Response status back from the server: " + status);
        return postMethod.getResponseBodyAsString();
    }


    /**
     * build request URLs 
     */
    protected String buildAlfrescoRequestURL(String uri, Map<String, String> params) throws Exception {
        String url = "";
        String serviceUrlBase = "http://127.0.0.1:8080/alfresco/service";
        String ticket = getAlfTicket();

        if(params != null) {
            for(String key : params.keySet()) {
                uri = uri.replace("{"+key+"}", URLEncoder.encode(params.get(key), "utf-8"));
            }
        }

        url = serviceUrlBase + uri;
        url += (url.contains("?")) ? "&alf_ticket="+ticket : "?alf_ticket="+ticket;
        
        return url;
    }

    /**
     * Get the alfresco ticket from the URL or the cookie or from an authorinization
     */
    protected String getAlfTicket() {
        String ticket = "UNSET";
        RequestContext context = RequestContext.getCurrent();

        if (context != null) {
            HttpSession httpSession = context.getRequest().getSession();
            String sessionTicket = (String)httpSession.getValue("alf_ticket");

            if(sessionTicket != null) {
                ticket = sessionTicket;
            }

        } else {
            CronJobContext cronJobContext = CronJobContext.getCurrent();
            if (cronJobContext != null) {
                ticket = cronJobContext.getAuthenticationToken();
            }
        }

        return ticket;
    }

    @Override
    public Map<String, String> getUserProfile(String username) {
        addDebugStack();
        InputStream retStream = null;
        Map<String, String> toRet = new HashMap<String,String>();
        try {
            // construct and execute url to download result
            // TODO: use alfresco/service/api/sites/craftercms250/memberships/admin instead
            String downloadURI = "/api/people/{username}";
            Map<String, String> lookupContentParams = new HashMap<String, String>();
            lookupContentParams.put("username", username);

            retStream = this.alfrescoGetRequest(downloadURI, lookupContentParams);
 
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> result = objectMapper.readValue(retStream, HashMap.class);

            toRet.put("userName", (String)result.get("userName"));
            toRet.put("firstName", (String)result.get("firstName"));
            toRet.put("lastName", (String)result.get("lastName"));
            toRet.put("email", (String)result.get("email"));
            //toRet.put("profileImage", result.get("NOTSET"));
        }
        catch(Exception err) {
            logger.error("err getting user profile: ", err);
        }

        return toRet;
    }

    @Override
    public Set<String> getUserGroups(String username) {
        addDebugStack();
        InputStream retStream = null;
        Set<String> toRet = new HashSet<String>();
        try {
            // construct and execute url to download result
            // TODO: use alfresco/service/api/sites/craftercms250/memberships/admin instead
            String downloadURI = "/api/people/{username}?groups=true";
            Map<String, String> lookupContentParams = new HashMap<String, String>();
            lookupContentParams.put("username", username);

            retStream = this.alfrescoGetRequest(downloadURI, lookupContentParams);

            ///JSONObject jsonObject =
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> result = objectMapper.readValue(retStream, HashMap.class);

            List<Map<String, String>> groups = (List<Map<String, String>>)result.get("groups");
            for (Map<String, String> group : groups) {
                toRet.add(group.get("displayName"));
            }
/*

            JsonFactory jsonFactory = new JsonFactory();
            JsonParser parser = jsonFactory.createJsonParser(retStream);

            // loop until token equal to "}"
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldname = parser.getCurrentName();
                if ("groups".equals(fieldname)) {
                    // current token is "[", move next
                    parser.nextToken();
                    // groups is array, loop until token equal to "]"
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        fieldname = parser.getCurrentName();
                        if ("displayName".equals(fieldname)) {
                            toRet.add(parser.getText());
                        }
                    }
                }
            }*/
        }
        catch(Exception err) {
            logger.error("err getting content: ", err);
        }
        return toRet;
    }

    @Override
    public String getCurrentUser() {
        addDebugStack();
        RequestContext context = RequestContext.getCurrent();
        HttpSession httpSession = context.getRequest().getSession();
        String username = (String)httpSession.getValue("username");
        return username;
    }

    @Override
    public String authenticate(String username, String password) {
        addDebugStack();
        InputStream retStream = null;
        String toRet = null;
        try {
            // construct and execute url to download result
            String downloadURI = "/api/login?u={u}&pw={pw}";
            Map<String, String> lookupContentParams = new HashMap<String, String>();
            lookupContentParams.put("u", username);
            lookupContentParams.put("pw", password);

            retStream = this.alfrescoGetRequest(downloadURI, lookupContentParams);

            SAXReader reader = new SAXReader();
            Document response = reader.read(retStream);
            Node ticketNode = response.selectSingleNode("//ticket");
            toRet = ticketNode.getText();
        }
        catch(Exception err) {
            logger.error("err getting content: ", err);
        }
        return toRet;
    }

    private void addDebugStack() {
        if (logger.getLevel().equals(Logger.LEVEL_DEBUG)) {
            Thread thread = Thread.currentThread();
            String threadName = thread.getName();
            logger.debug("Thread: " + threadName);
            StackTraceElement[] stackTraceElements = thread.getStackTrace();
            StringBuilder sbStack = new StringBuilder();
            int stackSize = (10 < stackTraceElements.length-2) ? 10 : stackTraceElements.length;
            for (int i = 2; i < stackSize+2; i++){
                sbStack.append("\n\t").append(stackTraceElements[i].toString());
            }
            RequestContext context = RequestContext.getCurrent();
            CronJobContext cronJobContext = CronJobContext.getCurrent();
            if (context != null) {
                HttpServletRequest request = context.getRequest();
                String url = request.getRequestURI() + "?" + request.getQueryString();
                logger.debug("Http request: " + url);
            } else if (cronJobContext != null) {
                logger.debug("Cron Job");

            }
            logger.debug("Stack trace (depth 10): " + sbStack.toString());
        }
    }

    protected String getNodeRefForPathCMIS(String fullPath) {
        Map<String, String> params = new HashMap<String, String>();
        String cleanPath = fullPath.replaceAll("//", "/"); // sometimes sent bad paths
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        Session session = getCMISSession();
        CmisObject cmisObject = session.getObjectByPath(cleanPath);
        String nodeRef = null;
        if (cmisObject != null) {
            Property property = cmisObject.getProperty("alfcmis:nodeRef");
            nodeRef = property.getValueAsString();
        }
        return nodeRef;
    }

    protected InputStream getContentStreamCMIS(String fullPath) throws ContentNotFoundException {
        Map<String, String> params = new HashMap<String, String>();
        String cleanPath = fullPath.replaceAll("//", "/"); // sometimes sent bad paths
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        InputStream inputStream = null;
        try {
            Session session = getCMISSession();
            CmisObject cmisObject = session.getObjectByPath(cleanPath);
            String nodeRef = null;
            if (cmisObject != null) {
                ObjectType type = cmisObject.getType();
                if (FolderType.DOCUMENT_BASETYPE_ID.equals(type.getId())) {
                    org.apache.chemistry.opencmis.client.api.Document document = (org.apache.chemistry.opencmis.client.api.Document) cmisObject;
                    ContentStream contentStream = document.getContentStream();
                    inputStream = contentStream.getStream();
                }
            }
        } catch (CmisBaseException e) {
            logger.error("Error getting content from CMIS repository for path: ", e, fullPath);
            throw new ContentNotFoundException(e);
        }
        return inputStream;
    }

    protected RepositoryItem[] getContentChildrenCMIS(String fullPath) {
        Map<String, String> params = new HashMap<String, String>();
        String cleanPath = fullPath.replaceAll("//", "/"); // sometimes sent bad paths
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        Session session = getCMISSession();
        CmisObject cmisObject = session.getObjectByPath(cleanPath);
        String nodeRef = null;
        RepositoryItem[] items = null;
        if (cmisObject != null) {
            ObjectType type = cmisObject.getBaseType();
            if (FolderType.FOLDER_BASETYPE_ID.equals(type.getId())) {
                Folder folder = (Folder) cmisObject;
                ItemIterable<CmisObject> children = folder.getChildren();
                List<RepositoryItem> tempList = new ArrayList<RepositoryItem>();
                Iterator<CmisObject> iterator = children.iterator();
                while (iterator.hasNext()) {
                    CmisObject child = iterator.next();

                    boolean isFolder = FolderType.FOLDER_BASETYPE_ID.equals(child.getBaseType().getId());
                    RepositoryItem item = new RepositoryItem();
                    item.name = child.getName();
                    if (child.getType().isFileable()) {
                        FileableCmisObject fileableCmisObject = (FileableCmisObject) child;
                        item.path = fileableCmisObject.getPaths().get(0);
                    } else {
                        item.path = fullPath;
                    }
                    item.path = item.path.replace("/" + item.name, "");
                    item.isFolder = isFolder;

                    tempList.add(item);
                }
                items = new RepositoryItem[tempList.size()];
                items = tempList.toArray(items);
            }
        }
        return items;
    }

    protected Session getCMISSession() {
        // Create a SessionFactory and set up the SessionParameter map
        SessionFactory sessionFactory = SessionFactoryImpl.newInstance();
        Map<String, String> parameter = new HashMap<String, String>();

        // user credentials - using the standard admin/admin
        String ticket = getAlfTicket();
        parameter.put(SessionParameter.USER, "ROLE_TICKET");
        parameter.put(SessionParameter.PASSWORD, ticket);

        // connection settings - we're connecting to a public cmis repo,
        // using the AtomPUB binding, but there are other options here,
        // or you can substitute your own URL
        parameter.put(SessionParameter.ATOMPUB_URL, "http://localhost:8080/alfresco/api/-default-/public/cmis/versions/1.1/atom/");
        parameter.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());

        // find all the repositories at this URL - there should only be one.
        List<Repository> repositories = new ArrayList<Repository>();
        repositories = sessionFactory.getRepositories(parameter);

        // create session with the first (and only) repository
        Repository repository = repositories.get(0);
        parameter.put(SessionParameter.REPOSITORY_ID, repository.getId());
        Session session = sessionFactory.createSession(parameter);

        return session;
    }

    public void lockItem(String site, String path) {

    }

    public void unLockItem(String site, String path) {

    }


    public Reactor getRepositoryReactor() { return repositoryReactor; }
    public void setRepositoryReactor(Reactor repositoryReactor) { this.repositoryReactor = repositoryReactor; }

    protected Reactor repositoryReactor;
}