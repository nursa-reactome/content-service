package org.reactome.server.tools.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.reactome.server.tools.interactors.exception.InvalidInteractionResourceException;
import org.reactome.server.tools.interactors.model.Interaction;
import org.reactome.server.tools.interactors.model.InteractionResource;
import org.reactome.server.tools.interactors.model.InteractorResource;
import org.reactome.server.tools.interactors.service.InteractionResourceService;
import org.reactome.server.tools.interactors.service.InteractionService;
import org.reactome.server.tools.interactors.service.InteractorResourceService;
import org.reactome.server.tools.model.Entity;
import org.reactome.server.tools.model.InteractionResult;
import org.reactome.server.tools.model.InteractorResult;
import org.reactome.server.tools.model.Synonym;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.*;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

@RestController
@Api(value = "/interactors", description = "Static content")
@RequestMapping("/interactors/static/")
public class InteractionController {

    /**
     * Create the service that queries DB
     **/
    private InteractionService interactionService = InteractionService.getInstance();
    private InteractionResourceService interactionResourceService = InteractionResourceService.getInstance();
    private InteractorResourceService interactorResourceService = InteractorResourceService.getInstance();

    /**
     * These attributes will be used to cache the resource
     **/
    private Map<String, InteractorResource> interactorResourceMap = new HashMap<>();
    private Map<String, InteractionResource> interactionResourceMap = new HashMap<>();

    @ApiOperation(value = "Retrieve a summary of a given accession by resource", response = InteractionResult.class)
    @RequestMapping(value = "/protein/{acc}/summary", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public InteractionResult getProteinSummaryByResourceAndAcc(@PathVariable String acc) {
        List<String> accs = new ArrayList<>(1);
        accs.add(acc);

        return getProteinsSummary(accs, "IntAct");
    }

    @ApiOperation(value = "Retrieve a detailed interaction information of a given accession by resource", response = InteractionResult.class)
    @RequestMapping(value = "/protein/{acc}/details", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public InteractionResult getProteinDetailsByResourceAndAcc(@PathVariable String acc,
                                                               @RequestParam(value = "page", required = false) Integer page,
                                                               @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        if (page == null) {
            page = -1;
        }

        if (pageSize == null) {
            pageSize = -1;
        }

        List<String> accs = new ArrayList<>(1);
        accs.add(acc);

        return getProteinDetails(accs, "IntAct", page, pageSize);
    }

    @ApiOperation(value = "Retrieve a summary of a given accession list by resource", response = InteractionResult.class)
    @RequestMapping(value = "//proteins/summary", method = RequestMethod.POST, consumes = "text/plain", produces = "application/json")
    @ResponseBody
    public InteractionResult getProteinsSummaryByResource(@RequestBody String proteins) {
        /** Split param and put into a Set to avoid duplicates **/
        Set<String> accs = new HashSet<>(Arrays.asList(proteins.split("\\s*,\\s*")));

        return getProteinsSummary(accs, "IntAct");

    }

    @ApiOperation(value = "Retrieve a detailed interaction information of a given accession by resource", response = InteractionResult.class)
    @RequestMapping(value = "/proteins/details", method = RequestMethod.POST, consumes = "text/plain", produces = "application/json")
    @ResponseBody
    public InteractionResult getProteinsDetailsByResource(@RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "pageSize", required = false) Integer pageSize, @RequestBody String proteins) {
        if (page == null) {
            page = -1;
        }

        if (pageSize == null) {
            pageSize = -1;
        }

        /** Split param and put into a Set to avoid duplicates **/
        Set<String> accs = new HashSet<>(Arrays.asList(proteins.split("\\s*,\\s*")));

        return getProteinDetails(accs, "IntAct", page, pageSize);
    }

    /**
     * Caching resources for easy access during json handling
     *
     * @throws SQLException
     */
    private void cacheResources() throws SQLException {
        List<InteractionResource> interactionResourceList = interactionResourceService.getAll();
        for (InteractionResource interactionResource : interactionResourceList) {
            interactionResourceMap.put(interactionResource.getName().toLowerCase(), interactionResource);
        }

        List<InteractorResource> interactorResourceList = interactorResourceService.getAll();
        for (InteractorResource interactorResource : interactorResourceList) {
            interactorResourceMap.put(interactorResource.getName().toLowerCase(), interactorResource);
        }
    }

    /**
     * Generic method that queries database and build the JSON
     *
     * @return InteractionResult
     */
    private InteractionResult getProteinDetails(Collection<String> accs, String resource, Integer page, Integer pageSize) {
        /** Json Result **/
        InteractionResult interactionResult = new InteractionResult();

        try {

            /** caching resources, get values from tha Map **/
            cacheResources();

            /** Query database. Generic Layer. Don't need to know the DB to communicate here **/
            Map<String, List<Interaction>> interactionMaps = interactionService.getInteractions(accs, resource, page, pageSize);

            /** Get interaction resource from cache **/
            InteractionResource interactionResource = interactionResourceMap.get(resource.toLowerCase());

            /** Get interactor resource from cache **/ // TODO define what should be done here

            /** Entities are a JSON Object **/
            List<Entity> entities = new ArrayList<>();

            /** Synomys are a JSON Object **/
            Map<String, Synonym> synonymsMaps = new HashMap<>();

            for (String accKey : interactionMaps.keySet()) {

                List<Interaction> interactions = interactionMaps.get(accKey);

                interactionResult.setResource(resource); // cache resource and get it from there
                interactionResult.setInteractorUrl(""); // TODO sometimes a protein interacts with chebi, consider both urls here + type in the json
                interactionResult.setInteractionUrl(interactionResource.getUrl());

                Entity entity = new Entity();
                entity.setAcc(accKey);
                entity.setCount(interactions.size());

                List<InteractorResult> interactorsResultList = new ArrayList<>();
                for (Interaction interaction : interactions) {
                    InteractorResult interactor = new InteractorResult();
                    interactor.setAcc(interaction.getInteractorB().getAcc());
                    interactor.setScore(interaction.getIntactScore());

                    // TODO: many interaction ID for the same interaction. Sent an email to tony and we will figure out. Now I just get the first one
                    interactor.setInteractionId(interaction.getInteractionDetailsList().get(0).getInteractionAc());

                    /** Creating synonym **/
                    Synonym synonym = new Synonym();
                    synonym.setAcc(interaction.getInteractorB().getAcc());
                    synonym.setImageUrl(null); // TODO define image in the interactor ?
                    synonym.setText(interaction.getInteractorB().getAlias());
                    synonymsMaps.put(synonym.getAcc(), synonym);

                    interactorsResultList.add(interactor);
                }

                entity.setInteractors(interactorsResultList);

                entities.add(entity);

                interactionResult.setEntities(entities);

                interactionResult.setSynonym(synonymsMaps);

            }

        } catch (SQLException | InvalidInteractionResourceException s) {
            s.printStackTrace();
            interactionResult.setMessage(s.getMessage());
        }

        return interactionResult;
    }

    /**
     * Generic method that queries the database and build the JSON Object
     *
     * @return InteractionResult
     */
    private InteractionResult getProteinsSummary(Collection<String> accs, String resource) {
        /** Json Result **/
        InteractionResult interactionResult = new InteractionResult();

        try {

            /** Query database and get the count **/
            Map<String, Integer> interactionCountMap = interactionService.countInteractionsByAccessions(accs, resource);

            /** Entities are a JSON Object **/
            List<Entity> entities = new ArrayList<>();

            interactionResult.setResource(resource);

            for (String accKey : interactionCountMap.keySet()) {
                Integer count = interactionCountMap.get(accKey);

                Entity entity = new Entity();
                entity.setAcc(accKey);
                entity.setCount(count);

                entities.add(entity);
            }

            interactionResult.setEntities(entities);

        } catch (SQLException | InvalidInteractionResourceException s) {
            s.printStackTrace();
            interactionResult.setMessage(s.getMessage());
        }

        return interactionResult;
    }
}

/**
 * NEXT STEPS
 * <p>
 * 1- Work in the pagination and pageSize results - OK
 * 2- Work in the summary - OK
 * 3- interactor url - this has to be fixed (discuss)
 **/