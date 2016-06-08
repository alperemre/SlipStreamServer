package com.sixsq.slipstream.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sixsq.slipstream.util.ResourceTestBase;
import org.junit.Test;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.StringRepresentation;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 */
public class UIPlacementResourceTest extends ResourceTestBase {

    @Test
    public void putUiPlacementRejectsInvalidJson() throws Exception {
        Response response = putUIPlacement("XXX wrong JSON");
        assertThat(response.getStatus(), is(Status.CLIENT_ERROR_BAD_REQUEST));
    }

    @Test
    public void putUiPlacementReturnsValidJson() throws Exception {

        String data ="{" +
                "\"module-uri\": \"/module/examples/service-testing/apache\"," +
                "\"placement-params\": {}," +
                "\"prs-endpoint\": \"http://prs/filter-rank\"," +
                "\"user-connectors\": [\"cloud-1\", \"cloud-2\"]" +
                "}";

        Response response = putUIPlacement(data);

        Gson gson = new GsonBuilder().create();
        Object fromJson = gson.fromJson(response.getEntityAsText(), Object.class);

        assertNotNull("The response must be a valid Json", fromJson);
        assertThat(response.getStatus(), is(Status.SUCCESS_OK));
    }

    private Response putUIPlacement(String data) throws Exception {
        Request request = createPutRequest(null, new StringRepresentation(data, MediaType.APPLICATION_ALL_JSON), "ui/placement");
        return executeRequest(request, new UIPlacementResource());
    }

}
