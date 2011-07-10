package org.deepamehta.plugins.poemspace.importer;

/*
 * Copyright (c) 2011 - DeepaMehta e.V.
 *
 * This file is part of dm3-poemspace-importer
 *
 * This code is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * version 3 for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with this work. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONObject;

import de.deepamehta.core.Topic;
import de.deepamehta.core.model.ClientContext;
import de.deepamehta.core.model.Composite;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;

import de.deepamehta.core.service.Plugin;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.util.JavaUtils;
import java.util.Vector;
import java.util.logging.Level;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;

/**
 * A plugin to restore the application model and data of the deepamehta3 v0.3 dm3-poemspace-app based on a couchdb-dump.
 *
 * @author <a href="mailto:malte@deepamehta.org">Malte Reißig</a> - http://github.com/mukil
 * @modified Jul 10, 2011
 * @website http://github.com/mukil/dm3-poemspace-importer
 */

public class RestoringPoemSpacePlugin extends Plugin {

    private static final String POEMSPACE_WORKSPACE_NAME = "Poem-Space";
    private static final String COMMUNITY_MAILBOXES_BOOK = "/poemspace-dump.json";
    // 
    private long poemWorkspaceId; // keep this for clientContext, to associate all Persons to "Poem-Space"

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private WorkspacesService wsService;
    // private AccessControlService acService;

    // service availability book keeping
    private boolean performWorkspaceInitialization;
    // private boolean performACLInitialization;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // *********************************************
    // *** Hooks (called from DeepaMehta 3 Core) ***
    // *********************************************



    /**
     * Creates the "Poem-Space" workspace and import the /resources/community-tab.csv file.
     * <p>
     * Note: this relies on the Workspaces service.
     * If the services are not yet available the respective actions are postponed till the services arrive.
     * See {@link serviceArrived}.
     * <p>
     */
    @Override
    public void postInstallPluginHook() {
      performWorkspaceInitialization = true;
      // performACLInitialization = true;
      if (wsService != null) {
          logger.info("########## Clean install detected AND WorkspacesService already available " +
              "=> create Poem-Space workspace");
          createPoemspaceWorkspace();
      } else {
          logger.info("########## Clean install detected, WorkspacesService NOT yet available " +
              "=> create Poem-Space workspace later on");
      }
    }



    // ---

    @Override
    public void serviceArrived(PluginService service) {
      logger.info("########## Service arrived: " + service);
      if (service instanceof WorkspacesService) {
          wsService = (WorkspacesService) service;
          if (performWorkspaceInitialization) {
              logger.info("########## WorkspacesService arrived AND clean install detected => " +
                  "create Poem-Space workspace");
              createPoemspaceWorkspace();
          } else {
              logger.info("########## WorkspacesService arrived, clean install NOT yet detected => " +
                  "possibly create Poem-Space workspace later on");
          }
      }

    }

    @Override
    public void serviceGone(PluginService service) {
      if (service instanceof WorkspacesService) {
          wsService = null;
      }

    }



    // ***********************
    // *** Command Handler ***
    // ***********************



    private void createPoemspaceWorkspace() {
      // create workspace
      Topic workspace = wsService.createWorkspace(POEMSPACE_WORKSPACE_NAME);
      poemWorkspaceId = workspace.getId();
      // assign "Event Category" type
      // TopicType apType = dms.getTopicType("org/deepamehta/topictype/dm3.poemspace.event_category", null);
      // wsService.assignType(workspace.id, apType.id);
      //
      parsePoemSpaceDump();
      // book keeping
      performWorkspaceInitialization = false;
    }

    private void parsePoemSpaceDump() {
      // read in community mailbox-book file
      try {
        logger.log(Level.INFO, "trying to readInPoemSpaceDump... ");
        InputStream in = getResourceAsStream(COMMUNITY_MAILBOXES_BOOK);
        if (in != null) {
          String data = JavaUtils.readText(in);
          logger.log(Level.INFO, "readInPoemSpaceDump... trying to parse it..");
          // Hashtable fieldIdxMap = getThunderbirdFieldIdxMap(headerEntries);
          Vector<Topic> personTopics = new Vector<Topic>();
          // first collecting all workspaces over all persons in memory
          Vector<String> workspaces = new Vector<String>();
          // little helper, collect all types present in the dump
          Vector<String> types = new Vector<String>();
          Vector<String> relationTypes = new Vector<String>();
          // start parsing the json-dump
          try {
            JSONObject json = new JSONObject(data);
            // start picking the apples..
            JSONArray rows = json.getJSONArray("rows");
            for (int k = 0; k < rows.length(); k++) {
              JSONObject row = rows.getJSONObject(k);
              JSONObject document = row.getJSONObject("doc");
              String docType = "";
              try {
                docType = document.getString("type");
              } catch (JSONException ex) {
                // just skipping these rows containing javascript
                // logger.info("some rows like these are neither topics nor relations, what are they?");
                // logger.info("row => \"" + row.toString() + "\"");
                // logger.info("cause: " + ex.getCause() + " message: " + ex.getMessage());
              }
              if (docType.equals("Relation")) {
                String relationType = document.getString("rel_type");
                if (!relationTypes.contains(relationType)) {
                  relationTypes.add(relationType);
                }
                // skipping for now
              } else if (docType.equals("Topic")){
                String topicType = document.getString("topic_type");
                if (topicType.equals("Person")) {
                  JSONArray fields = document.getJSONArray("fields");
                  //
                  String firstName = "";
                  String eMail = "";
                  String phone = "";
                  String website = "";
                  String address = "";
                  String notes = "";
                  //
                  // TODO: custom related types
                  String district = "";
                  String hood = "";
                  String artCategory = "";
                  for (int m = 0; m < fields.length(); m++) {
                    JSONObject field = fields.getJSONObject(m);
                    String type = field.getString("id");
                    if (!types.contains(type)) {
                      types.add(type);
                    }
                    if (type != null) {
                      //
                      if (type.equals("Name")) {
                        firstName = field.getString("content");
                      } else if (type.equals("Email")) {
                        eMail = field.getString("content");
                      } else if (type.equals("Phone")) {
                        phone = field.getString("content");
                      } else if (type.equals("Address")) {
                        address = field.getString("content");
                      } else if (type.equals("Notes")) {
                        notes = field.getString("content");
                      } else if (type.equals("Workspace")) {
                        workspaces.add(field.getString("content"));
                      } else if (type.equals("Website")) {
                        website = field.getString("content");
                      }
                    } else {
                      logger.fine("*** poemSpaceParser found NULL type..");
                    }
                  }
                  //
                  Topic personTopic = createPersonTopic(firstName, address, eMail, phone, notes, website);
                  personTopics.add(personTopic);
                }
              } else {
                logger.fine("ERROR something strange happened to your dump.. ");
              }
            }
          } catch(JSONException ex) {
            logger.info("cause: " + ex.getCause() + " message: " + ex.getMessage());
            throw new RuntimeException("ERROR during deciphering of all JSON Objects", ex);
          }
          logger.info("### Start Dump Report ###");
          logger.info("- created " + personTopics.size() + " Persons .. ");
          logger.info("- identified "+ types.size() + " TopicTypes and " + relationTypes.size() + " RelationTypes");
          logger.info("-- TopicTypes were => " + types.toString());
          logger.info("-- RelationTypes were => " + relationTypes.toString());
          logger.info("### End Dump Report ###");
        }
      } catch (IOException iex) {
        throw new RuntimeException("ERROR while reading in \""+COMMUNITY_MAILBOXES_BOOK+"\"-file", iex);
      }
    }

    private Topic createPersonTopic(String firstName, String address, String eMail, String phone, String notes, String website) {
      //
      // logger.info("\t fetched Person: " + firstName + ", " + eMail);
      // JSONObject person = new JSONObject();
      TopicModel personTopic = new TopicModel("dm3.contacts.person");
      Composite person = new Composite();
      // 
      Composite nameComposite = new Composite();
      nameComposite.put("dm3.contacts.first_name", firstName);
      nameComposite.put("dm3.contacts.last_name", "");
      person.put("dm3.contacts.person_name", nameComposite);
      //
      person.put("dm3.contacts.email_address", eMail);
      person.put("dm3.contacts.notes", notes);
      // person.put("dm3.contacts.address_name", address);
      // person.put("dm3.contacts.website", website);
      // person.put("dm3.contacts.phone", phone);
      // 
      personTopic.setComposite(person);
      return dms.createTopic(personTopic, new ClientContext("dm3_workspace_id=" + poemWorkspaceId));
    }

}
