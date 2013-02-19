package com.sixsq.slipstream.persistence;

/*
 * +=================================================================+
 * SlipStream Server (WAR)
 * =====
 * Copyright (C) 2013 SixSq Sarl (sixsq.com)
 * =====
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -=================================================================-
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.sixsq.slipstream.common.util.CommonTestUtil;
import com.sixsq.slipstream.connector.local.LocalConnector;
import com.sixsq.slipstream.exceptions.NotFoundException;
import com.sixsq.slipstream.exceptions.SlipStreamException;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.run.RunFactory;
import com.sixsq.slipstream.run.RunView;
import com.sixsq.slipstream.statemachine.States;
import com.sixsq.slipstream.util.SerializationUtil;

public class RunTest {

	protected static User user = null;
	protected static ImageModule image = null;
	protected static ImageModule imageref = null;
	protected static ImageModule imagebase = null;
	protected static ImageModule imagenoref = null;

	private static String cloudServiceName = new LocalConnector()
	.getCloudServiceName();

	@BeforeClass
	public static void setupClass() throws ValidationException {
		imagebase = new ImageModule("test/imagebase");
		imagebase.setImageId("base_image_id", cloudServiceName);
		imagebase.setIsBase(true);
		imagebase.store();

		imageref = new ImageModule("test/imageref");
		imageref.setModuleReference(imagebase);
		imageref.setImageId("ref_image_id", cloudServiceName);
		imageref.store();

		image = new ImageModule("test/image");
		image.setModuleReference(imageref.getResourceUri());
		image.setRecipe("a recipe");
		image.store();

		imagenoref = new ImageModule("test/imagenoref");
		imagenoref.setRecipe("a recipe");
		imagenoref.store();

		imagenoref = new ImageModule("test/imagenoref");
		imagenoref.setRecipe("a recipe");
		imagenoref.store();

		user = CommonTestUtil.createTestUser();
	}

	@AfterClass
	public static void teardownClass() {
		imagebase.remove();
		imageref.remove();
		image.remove();
		CommonTestUtil.deleteUser(user);
	}

	@Test
	public void verifyCorrectParameters() throws FileNotFoundException,
			IOException, SlipStreamException {

		Run run = RunFactory.getRun(image, cloudServiceName, user);

		assertEquals(image.getResourceUri(), run.getModuleResourceUrl());
		assertEquals(ModuleCategory.Image, run.getCategory());
		assertNotNull(run.getResourceUri());
		assertNotNull(run.getUuid());
	}

	@Test
	public void storeRetrieveAndDelete() throws FileNotFoundException,
			IOException, SlipStreamException {

		Run run = RunFactory.getRun(image, cloudServiceName, user);
		run.store();

		String resourceUrl = run.getResourceUri();

		Run runRestored = Run.load(resourceUrl);
		assertNotNull(runRestored);

		assertEquals(run.getResourceUri(), runRestored.getResourceUri());
		assertEquals(run.getModuleResourceUrl(),
				runRestored.getModuleResourceUrl());
		assertEquals(run.getCategory(), runRestored.getCategory());

		run.remove();
		runRestored = Run.load(resourceUrl);
		assertNull(runRestored);
	}

	@Test
	public void runWithOneParameter() throws FileNotFoundException,
			IOException, SlipStreamException {

		Run run = RunFactory.getRun(image, cloudServiceName, user);

		String resourceUrl = run.getResourceUri();

		String parameterName = "name";
		String description = "description";
		String value = "value";

		RunParameter parameter = new RunParameter(parameterName, value,
				description);
		run.setParameter(parameter);

		run.store();

		Run runRestored = Run.load(resourceUrl);
		assertNotNull(runRestored);

		Map<String, RunParameter> parameters = runRestored.getParameters();
		assertNotNull(parameters);
		assertEquals(1, parameters.size());

		parameter = parameters.get(parameterName);
		assertNotNull(parameter);
		assertEquals(parameterName, parameter.getName());
		assertEquals(description, parameter.getDescription());
		assertEquals(value, parameter.getValue());

		run.remove();
		run = Run.load(resourceUrl);
		assertNull(run);
	}

	@Test
	public void runWithTwoParameters() throws FileNotFoundException,
			IOException, SlipStreamException {

		Run run = RunFactory.getRun(image, cloudServiceName, user);

		String resourceUrl = run.getResourceUri();

		String parameterName1 = "p1";
		String description1 = "d1";
		String value1 = "v1";

		RunParameter parameter = new RunParameter(parameterName1, value1,
				description1);
		run.setParameter(parameter);

		String parameterName2 = "p2";
		String description2 = "d2";
		String value2 = "v2";

		parameter = new RunParameter(parameterName2, value2, description2);
		run.setParameter(parameter);

		run.store();

		Run runRestored = Run.load(resourceUrl);
		assertNotNull(runRestored);

		Map<String, RunParameter> parameters = runRestored.getParameters();
		assertNotNull(parameters);
		assertEquals(2, parameters.size());

		parameter = parameters.get(parameterName1);
		assertNotNull(parameter);
		assertEquals(parameterName1, parameter.getName());
		assertEquals(description1, parameter.getDescription());
		assertEquals(value1, parameter.getValue());

		parameter = parameters.get(parameterName2);
		assertNotNull(parameter);
		assertEquals(parameterName2, parameter.getName());
		assertEquals(description2, parameter.getDescription());
		assertEquals(value2, parameter.getValue());

		run.remove();
		run = Run.load(resourceUrl);
		assertNull(run);
	}

	@Test
	public void assignRuntimeParameters() throws FileNotFoundException,
			IOException, SlipStreamException {

		Run run = RunFactory.getRun(image, cloudServiceName, user);

		int initialParameterNo = run.getRuntimeParameters().size();

		String resourceUrl = run.getResourceUri();

		String k1 = "node.1:k1";
		String v1 = "v1";
		String v1Desc = "v1 desc";

		run.assignRuntimeParameter(k1, v1, v1Desc);

		String k2 = "node.2:k2";
		String v2 = "v2";
		String v2Desc = "v2 desc";

		run.assignRuntimeParameter(k2, v2, v2Desc);

		run.store();

		Map<String, String> data = new HashMap<String, String>();
		data.put(k1, v1);
		data.put(k2, v2);

		EntityManager em = PersistenceUtil.createEntityManager();
		EntityTransaction transaction = em.getTransaction();
		transaction.begin();

		Run runRestored = em.merge(Run.load(resourceUrl));
		assertNotNull(runRestored);

		Map<String, RuntimeParameter> parameters = runRestored
				.getRuntimeParameters();

		assertNotNull(parameters);
		assertEquals(initialParameterNo + data.size(), parameters.size());

		transaction.commit();
		em.close();

		assertEquals(v1, parameters.get(k1).getValue());
		assertEquals(v1Desc, parameters.get(k1).getDescription());

		assertEquals(v2, parameters.get(k2).getValue());
		assertEquals(v2Desc, parameters.get(k2).getDescription());

		run.remove();
		run = Run.load(resourceUrl);
		assertNull(run);

	}

	@Test
	public void createAndRetreive() throws FileNotFoundException, IOException,
			SlipStreamException {
		
		Run run = new Run(new ImageModule("createAndRetreive"), cloudServiceName, user);
		run.store();

		Run runRestored = Run.loadFromUuid(run.getUuid());

		assertNotNull(runRestored);
		assertEquals(run.getUuid(), runRestored.getUuid());

		run.remove();
	}

	@Test
	public void viewListByModule() throws FileNotFoundException, IOException,
			SlipStreamException {

		Authz authz;
		ImageModule findit = new ImageModule("findit");
		authz = new Authz("test", findit);
		findit.setAuthz(authz);
		findit.setModuleReference(image);
		findit.setRecipe("a recipe");
		findit.store();
		Metadata run1 = createAndStoreRun(findit);
		Metadata run2 = createAndStoreRun(findit);

		ImageModule dontfindit = new ImageModule("dontfindit");
		dontfindit.setModuleReference(image.getResourceUri());
		dontfindit.setRecipe("a recipe");
		authz = new Authz("test", dontfindit);
		dontfindit.setAuthz(authz);
		dontfindit.store();
		Metadata run3 = createAndStoreRun(dontfindit);

		List<RunView> runList = Run.viewList(findit.getResourceUri(), new User("user"));

		assertEquals(2, runList.size());

		run1.remove();
		run2.remove();
		run3.remove();

		findit.remove();
		dontfindit.remove();
	}

	@Test
	public void onlyViewListMyRuns() throws FileNotFoundException, IOException,
			SlipStreamException {

		Authz authz;
		ImageModule image = new ImageModule("onlyViewListMyRuns");
		authz = new Authz("test", image);
		image.setAuthz(authz);
		image.setModuleReference(RunTest.image);
		image.setRecipe("a recipe");
		image.store();
		Metadata myRun = createAndStoreRun(image);
		Metadata myOtherRun = createAndStoreRun(image);
		Metadata notMyRun = createAndStoreRun(image, "other");

		List<RunView> runList = Run.viewList(image.getResourceUri(), new User("user"));

		assertEquals(2, runList.size());

		myRun.remove();
		myOtherRun.remove();
		notMyRun.remove();

		image.remove();
	}

	private Metadata createAndStoreRun(Module module)
			throws SlipStreamException {

		return createAndStoreRun(module, "user");
	}

	private Metadata createAndStoreRun(Module module, String user)
			throws SlipStreamException {

		Run run = RunFactory.getRun(module, cloudServiceName, RunTest.user);
		run.setUser(user);
		run.store();
		return run;
	}

	@Test
	public void checkRunSerialization() throws FileNotFoundException,
			IOException, SlipStreamException {

		Run run = RunFactory.getRun(image, cloudServiceName, user);

		SerializationUtil.toXmlString(run);
	}

	@Test
	public void testParameters() throws SlipStreamException,
			FileNotFoundException, IOException {

		Run run = RunFactory.getRun(image, cloudServiceName, user);

		run.setParameter(new RunParameter("k1", "v1", ""));
		run.setParameter(new RunParameter("k2", "v2", ""));

		assertNotSame(0, run.getParameters().size());

		RunParameter p;
		p = run.getParameter("k1");
		assertEquals("k1", p.getName());
		assertEquals("v1", p.getValue());

		p = run.getParameter("k2");
		assertEquals("k2", p.getName());
		assertEquals("v2", p.getValue());
	}

	@Test
	public void updateRuntimeParameters() throws SlipStreamException,
			FileNotFoundException, IOException {

		Run run = RunFactory.getRun(image, cloudServiceName, user);

		run.assignRuntimeParameter("node.1:k1", "v1 init", "v1 desc");
		run.assignRuntimeParameter("node.1:k2", "v2 init", "v2 desc");

		run.updateRuntimeParameter("node.1:k1", "v1");
		run.updateRuntimeParameter("node.1:k2", "v2");

		assertEquals("v1", run.getRuntimeParameterValue("node.1:k1"));
		assertEquals("v2", run.getRuntimeParameterValue("node.1:k2"));
	}

	@Test
	@Ignore
	public void finalStateFromAborting() throws FileNotFoundException,
			IOException, SlipStreamException {
		// Test that if the abort flag is set, the final state of the execution
		// is Aborted

		String key = RuntimeParameter.GLOBAL_STATE_KEY;

		Run run = RunFactory.getRun(image, cloudServiceName, user);

		run.updateRuntimeParameter(RuntimeParameter.GLOBAL_ABORT_KEY, "Oops");
		assertEquals(RunStatus.FAILING,
				run.getRuntimeParameterValueIgnoreAbort(key));
		run.updateRuntimeParameter(RuntimeParameter.GLOBAL_ABORT_KEY,
				States.Terminal.toString());
		assertEquals(RuntimeParameter.GLOBAL_ABORT_KEY,
				run.getRuntimeParameterValueIgnoreAbort(key));
	}

	@Test(expected = NotFoundException.class)
	public void setInexistantRuntimeParameter() throws SlipStreamException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
		CommonTestUtil.resetAndLoadConnector(com.sixsq.slipstream.connector.local.LocalConnector.class);
		
		ImageModule image = new ImageModule(
				"setInexistantRuntimeParameterImage");
		image.setImageId("123", cloudServiceName);
		ModuleParameter moduleParameter;

		moduleParameter = new ModuleParameter("pi1", null, "");
		moduleParameter.setCategory(ParameterCategory.Input);
		image.setParameter(moduleParameter);

		moduleParameter = new ModuleParameter("po1", null, "");
		moduleParameter.setCategory(ParameterCategory.Output);
		image.setParameter(moduleParameter);

		DeploymentModule deployment = new DeploymentModule(
				"setInexistantRuntimeParameter");

		NodeParameter parameter;

		Node node1 = new Node("node1", image);
		deployment.getNodes().put(node1.getName(), node1);
		Node node2 = new Node("node2", image);
		deployment.getNodes().put(node2.getName(), node2);

		parameter = new NodeParameter("pi1", "node2:po1", null);
		parameter.setContainer(node1);
		node1.setParameterMapping(parameter, deployment);

		parameter = new NodeParameter("pi1", "node1:po1", null);
		parameter.setContainer(node2);
		node2.setParameterMapping(parameter, deployment);
		
		deployment.store();

		Run run = RunFactory.getRun(deployment, cloudServiceName, user);
		
		try {
			run.updateRuntimeParameter("node1.1:notthere", null);
		} finally {
			deployment.remove();
		}
	
	}

}
