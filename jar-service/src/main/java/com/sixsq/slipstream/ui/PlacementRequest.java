package com.sixsq.slipstream.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sixsq.slipstream.configuration.Configuration;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.persistence.Module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Java structure sent to PRS-lib (thanks to asMap method).
 *
 * Responsible to load module object from given URI.
 *
 * @see UIPlacementResource
 */
public class PlacementRequest {

    public static final String SLIPSTREAM_PRS_ENDPOINT_PROPERTY_KEY = "slipstream.prs.endpoint";

    private static Logger logger = Logger.getLogger(PlacementRequest.class.getName());

    private static Configuration configuration;
    static {
        try {
            configuration = Configuration.getInstance();
        } catch (ValidationException ve) {
            logger.severe("Unable to access configuration. Cause: " + ve.getMessage());
        }
    }

    private String moduleUri;

    protected void setModule(Module module) {
        this.module = module;
    }

    public Module getModule(){
        if(module == null) {
            module = Module.load(moduleUri);
        }

        logger.fine("Loaded module " + module);
        return module;
    }

    private Module module;

    private Map<Object, Object> placementParams;

    private String prsEndPoint;

    private List<String> userConnectors;

    public Map<String, Object> asMap() {

        Map<String, Object> result = new HashMap<>();

        result.put("module", getModule());
        result.put("user-connectors", userConnectors);

        result.put("placement-params", new HashMap<>());
        result.put("prs-endpoint", prsEndPoint);

        return result;
    }

    public static PlacementRequest fromJson(String json) {
        Gson gson = new GsonBuilder().create();
        PlacementRequest placementRequest = gson.fromJson(json, PlacementRequest.class);

        placementRequest.prsEndPoint = configuration.getProperty(SLIPSTREAM_PRS_ENDPOINT_PROPERTY_KEY);
        logger.info("PRS endpoint " + placementRequest.prsEndPoint);

        return placementRequest;
    }

    public String toString() {
        return moduleUri + ", " + prsEndPoint + ", " + userConnectors + ", " + placementParams;
    }


}