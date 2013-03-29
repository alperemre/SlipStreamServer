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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.MapKey;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Query;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;

import com.sixsq.slipstream.connector.Connector;
import com.sixsq.slipstream.connector.ConnectorFactory;
import com.sixsq.slipstream.connector.Credentials;
import com.sixsq.slipstream.exceptions.AbortException;
import com.sixsq.slipstream.exceptions.ConfigurationException;
import com.sixsq.slipstream.exceptions.NotFoundException;
import com.sixsq.slipstream.exceptions.SlipStreamException;
import com.sixsq.slipstream.exceptions.SlipStreamInternalException;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.run.RunDeploymentFactory;
import com.sixsq.slipstream.run.RunView;
import com.sixsq.slipstream.statemachine.States;

@SuppressWarnings("serial")
@Entity
@NamedQueries({
		@NamedQuery(name = "allActiveRunViews", query = "SELECT r FROM Run r ORDER BY r.startTime DESC"),
		@NamedQuery(name = "activeRunViews", query = "SELECT r FROM Run r WHERE r.user_ = :user ORDER BY r.startTime DESC"),
		@NamedQuery(name = "activeRunViewsByRefModule", query = "SELECT r FROM Run r WHERE r.user_ = :user AND r.moduleResourceUri = :referenceModule ORDER BY r.startTime DESC"),
		@NamedQuery(name = "getRunByInstanceId", query = "SELECT r FROM Run r JOIN r.runtimeParameters p WHERE r.user_ = :user AND p.key_ LIKE '%:instanceid' AND p.value = :instanceid ORDER BY r.startTime DESC") })
public class Run extends Parameterized<Run, RunParameter> {

	public static final String NODE_NAME_PARAMETER_SEPARATOR = "--";
	// Orchestrator
	public final static String ORCHESTRATOR_NAME = "orchestrator";
	public final static String ORCHESTRATOR_NAME_PREFIX = ORCHESTRATOR_NAME
			+ RuntimeParameter.NODE_PROPERTY_SEPARATOR;
	public static final String SERVICENAME_NODENAME_SEPARATOR = RuntimeParameter.NODE_PROPERTY_SEPARATOR;

	// Default machine name for image and disk creation
	public final static String MACHINE_NAME = "machine";
	public final static String MACHINE_NAME_PREFIX = MACHINE_NAME
			+ RuntimeParameter.NODE_PROPERTY_SEPARATOR;

	// The initial state of each node
	public final static String INITIAL_NODE_STATE_MESSAGE = States.Inactive
			.toString();
	public final static String INITIAL_NODE_STATE = States.Inactive.toString();

	public final static String RESOURCE_URI_PREFIX = "run/";

	public static Run abortOrReset(String abortMessage, String nodename,
			String uuid) {
		EntityManager em = PersistenceUtil.createEntityManager();
		EntityTransaction transaction = em.getTransaction();
		transaction.begin();

		Run run = Run.abortOrReset(abortMessage, nodename, em, uuid);

		transaction.commit();
		em.close();

		return run;
	}

	public static Run abortOrReset(String abortMessage, String nodename,
			EntityManager em, String uuid) {
		Run run = Run.loadFromUuid(uuid, em);
		RuntimeParameter globalAbort = getGlobalAbort(run);
		String nodeAbortKey = getNodeAbortKey(nodename);
		RuntimeParameter nodeAbort = run.getRuntimeParameters().get(
				nodeAbortKey);
		if ("".equals(abortMessage)) {
			globalAbort.reset();
			if (nodeAbort != null) {
				nodeAbort.reset();
			}
		} else if (!globalAbort.isSet()) {
			globalAbort.setValue(abortMessage);
			if (nodeAbort != null) {
				nodeAbort.setValue(abortMessage);
			}
		}

		return run;
	}

	private static RuntimeParameter getGlobalAbort(Run run) {
		RuntimeParameter abort = run.getRuntimeParameters().get(
				RuntimeParameter.GLOBAL_ABORT_KEY);
		return abort;
	}

	private static String getNodeAbortKey(String nodeName) {
		return nodeName + RuntimeParameter.NODE_PROPERTY_SEPARATOR
				+ RuntimeParameter.ABORT_KEY;
	}

	public static Run loadFromUuid(String uuid) {
		String resourceUri = RESOURCE_URI_PREFIX + uuid;
		return load(resourceUri);
	}

	public static Run loadFromUuid(String uuid, EntityManager em) {
		String resourceUri = RESOURCE_URI_PREFIX + uuid;
		return load(resourceUri, em);
	}

	public static Run load(String resourceUri) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Run run = em.find(Run.class, resourceUri);
		em.close();
		return run;
	}

	public static Run load(String resourceUri, EntityManager em) {
		Run run = em.find(Run.class, resourceUri);
		return run;
	}

	@SuppressWarnings("unchecked")
	public static List<RunView> viewListByInstanceId(User user,
			String instanceId) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("getRunByInstanceId");
		q.setParameter("user", user.getName());
		q.setParameter("instanceid", instanceId);
		List<Run> runs = q.getResultList();
		List<RunView> views = convertRunsToRunViews(runs, user);
		em.close();
		return views;
	}

	private static List<RunView> convertRunsToRunViews(List<Run> runs, User user) {
		List<RunView> views = new ArrayList<RunView>();
		RunView runView;
		try {
			runs = updateVmStatus(runs, user);
		} catch (SlipStreamException e1) {
			// It's ok to fail
		}
		for (Run r : runs) {
			runView = new RunView(r.getResourceUri(), r.getUuid(),
					r.getModuleResourceUrl(), r.getStatus(), r.getStart());
			try {
				runView.hostname = r
						.getRuntimeParameterValueIgnoreAbort(MACHINE_NAME_PREFIX
								+ RuntimeParameter.HOSTNAME_KEY);
				runView.vmstate = r
						.getRuntimeParameterValueIgnoreAbort(MACHINE_NAME_PREFIX
								+ RuntimeParameter.STATE_VM_KEY);
			} catch (NotFoundException e) {
			}
			views.add(runView);
		}
		return views;
	}

	// REMARK: LS: I think this method is useless
	private static List<Run> updateVmStatus(List<Run> runs, User user)
			throws SlipStreamException {
		/*
		 * Connector connector = ConnectorFactory.getCurrentConnector(user);
		 * Properties describeInstancesStates =
		 * connector.describeInstances(user); for (Run run : runs) {
		 * 
		 * run = populateVmStateProperties(run, describeInstancesStates); }
		 */
		return runs;
	}

	public static Run updateVmStatus(Run run, User user)
			throws SlipStreamException {
		Properties describeInstancesStates;

		if (run.getCategory() == ModuleCategory.Deployment) {
			describeInstancesStates = new Properties();
			HashSet<String> cloudServicesList = run.getCloudServicesList();

			for (String cloudServiceName : cloudServicesList) {
				Connector connector = ConnectorFactory
						.getConnector(cloudServiceName);
				Properties props = connector.describeInstances(user);
				for (String key : props.stringPropertyNames()) {
					describeInstancesStates.put(key, props.getProperty(key));
				}

			}
		} else {
			Connector connector = ConnectorFactory.getCurrentConnector(user);
			describeInstancesStates = connector.describeInstances(user);
		}

		return updateVmStatus(run, describeInstancesStates);
	}

	private static Run updateVmStatus(Run run,
			Properties describeInstancesStates) throws SlipStreamException {
		run = populateVmStateProperties(run, describeInstancesStates);
		return run;
	}

	public static Run populateVmStateProperties(Run run,
			Properties describeInstancesStates) throws NotFoundException,
			ValidationException {

		List<String> nodes = run.getNodeNameList();
		String vmIdKey;
		String vmId;
		String vmStateKey;

		for (String nodeName : nodes) {
			String keyPrefix = nodeName
					+ RuntimeParameter.NODE_PROPERTY_SEPARATOR;
			vmIdKey = keyPrefix + RuntimeParameter.INSTANCE_ID_KEY;
			vmId = run.getRuntimeParameterValueIgnoreAbort(vmIdKey);
			vmId = vmId == null ? "" : vmId;
			vmStateKey = keyPrefix + RuntimeParameter.STATE_VM_KEY;
			String vmState = describeInstancesStates.getProperty(vmId,
					"Unknown");
			try {
				run.updateRuntimeParameter(vmStateKey, vmState);
			} catch (NotFoundException e) {
				run.assignRuntimeParameter(vmStateKey, vmState,
						RuntimeParameter.STATE_VM_DESCRIPTION);
			}
		}

		return run;
	}

	@SuppressWarnings("unchecked")
	public static List<RunView> viewListAll(User user) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("allActiveRunViews");
		List<Run> runs = q.getResultList();
		List<RunView> views = convertRunsToRunViews(runs, user);
		em.close();
		return views;
	}

	@SuppressWarnings("unchecked")
	public static List<RunView> viewList(User user) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("activeRunViews");
		q.setParameter("user", user.getName());
		List<Run> runs = q.getResultList();
		List<RunView> views = convertRunsToRunViews(runs, user);
		em.close();
		return views;
	}

	@SuppressWarnings("unchecked")
	public static List<RunView> viewList(String moduleResourceUri, User user) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("activeRunViewsByRefModule");
		q.setParameter("user", user.getName());
		q.setParameter("referenceModule", moduleResourceUri);
		List<Run> runs = q.getResultList();
		List<RunView> views = convertRunsToRunViews(runs, user);
		em.close();
		return views;
	}

	@Attribute
	@Id
	private String resourceUri;

	@Attribute
	private String uuid;

	private String status;

	@Attribute(empty = "Orchestration")
	private RunType type = RunType.Orchestration;

	@Attribute
	private String cloudServiceName;

	@Attribute
	public String getStatus() {
		States globalState = States.valueOf(runtimeParameters.get(
				RuntimeParameter.GLOBAL_STATE_KEY).getValue());
		RunStatus status = new RunStatus(globalState, isAbort());
		this.status = status.toString();
		return this.status;
	}

	@Attribute
	public void setStatus(String status) {
		this.status = status;
	}

	@Attribute(required = false)
	@Enumerated(EnumType.STRING)
	public States getState() {
		return States.valueOf(runtimeParameters.get(
				RuntimeParameter.GLOBAL_STATE_KEY).getValue());
	}

	@Attribute
	private String moduleResourceUri;

	private transient Credentials credentials;

	@OneToMany(mappedBy = "container", cascade = CascadeType.ALL)
	@MapKey(name = "key_")
	@ElementMap(name = "runtimeParameters", required = false, data = true, valueType = RuntimeParameter.class)
	private Map<String, RuntimeParameter> runtimeParameters = new HashMap<String, RuntimeParameter>();

	@Attribute
	@Temporal(TemporalType.TIMESTAMP)
	private Date startTime;

	@Attribute(required = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date endTime;

	/**
	 * Comma separated list of node names E.g. apache1.1, apache1.2, ...
	 */
	@Attribute
	@Lob
	private String nodeNames = "";

	/**
	 * Comma separated list of nodes, including the associated orchestror name -
	 * e.g. orchestrator:apache1, orchestrator:testclient1, ... or
	 * orchestrator-stratuslab:apache1, orchestrator-openstack:testclient1, ...
	 */
	private String groups = "";

	@Attribute(name = "user", required = false)
	private String user_;

	@Element(required = false)
	private transient Module module;

	@SuppressWarnings("unused")
	private Run() throws NotFoundException {
	}

	public Module getModule() {
		return module;
	}

	public void setModule(Module module) throws ValidationException {
		setModule(module, false);
	}

	public void setModule(Module module, boolean populate)
			throws ValidationException {
		this.module = module;
		if (populate) {
			populateModule();
		}
	}

	public Run(Module module, String cloudServiceName, User user)
			throws ValidationException {
		this(module, RunType.Orchestration, cloudServiceName, user);
	}

	public Run(Module module, RunType type, String cloudServiceName, User user)
			throws ValidationException {

		uuid = UUID.randomUUID().toString();
		resourceUri = RESOURCE_URI_PREFIX + uuid;

		this.category = module.getCategory();
		this.moduleResourceUri = module.getResourceUri();
		this.type = type;
		this.cloudServiceName = (CloudImageIdentifier.DEFAULT_CLOUD_SERVICE
				.equals(cloudServiceName) ? user.getDefaultCloudService()
				: cloudServiceName);
		this.user_ = user.getName();

		this.module = module;

		setStart();

		initializeParameters();
		initializeRuntimeParameters();
	}

	private void initializeParameters() throws ValidationException {

		if (getCategory() == ModuleCategory.Deployment) {
			DeploymentModule deployment = (DeploymentModule) module;
			for (Node node : deployment.getNodes().values()) {

				String nodeRuntimeParameterKeyName = nodeRuntimeParameterKeyName(
						node, RuntimeParameter.MULTIPLICITY_PARAMETER_NAME);
				setParameter(new RunParameter(nodeRuntimeParameterKeyName,
						String.valueOf(node.getMultiplicity()),
						RuntimeParameter.MULTIPLICITY_PARAMETER_DESCRIPTION));

				nodeRuntimeParameterKeyName = nodeRuntimeParameterKeyName(node,
						RuntimeParameter.CLOUD_SERVICE_NAME);
				setParameter(new RunParameter(nodeRuntimeParameterKeyName,
						String.valueOf(node.getCloudService()),
						RuntimeParameter.CLOUD_SERVICE_DESCRIPTION));
			}
		}
	}

	private void initializeRuntimeParameters() throws ValidationException {

		if (getType() == RunType.Orchestration) {
			if (getCategory() == ModuleCategory.Deployment) {
				HashSet<String> cloudServiceList = getCloudServicesList();
				for (String cloudServiceName : cloudServiceList) {
					initializeOrchestratorParameters("-" + cloudServiceName);
				}
			} else {
				initializeOrchestratorParameters();
			}
		}
		initializeGlobalParameters();
	}

	private void initializeGlobalParameters() throws ValidationException {

		assignRuntimeParameter(RuntimeParameter.GLOBAL_CATEGORY_KEY,
				getCategory().toString(), "Module category");

		assignRuntimeParameter(RuntimeParameter.GLOBAL_ABORT_KEY, "",
				RuntimeParameter.GLOBAL_ABORT_DESCRIPTION);
		assignRuntimeParameter(RuntimeParameter.GLOBAL_STATE_KEY,
				Run.INITIAL_NODE_STATE,
				RuntimeParameter.GLOBAL_STATE_DESCRIPTION);
		assignRuntimeParameter(RuntimeParameter.GLOBAL_STATE_MESSAGE_KEY,
				Run.INITIAL_NODE_STATE_MESSAGE,
				RuntimeParameter.GLOBAL_STATE_MESSAGE_DESCRIPTION);
		assignRuntimeParameter(RuntimeParameter.GLOBAL_TAGS_KEY, "",
				RuntimeParameter.GLOBAL_TAGS_DESCRIPTION);
		assignRuntimeParameter(RuntimeParameter.GLOBAL_NODE_GROUPS_KEY, "",
				RuntimeParameter.GLOBAL_NODE_GROUPS_DESCRIPTION);
	}

	private void initializeOrchestratorParameters() throws ValidationException {
		initializeOrchestratorParameters("");
	}

	private void initializeOrchestratorParameters(String suffix)
			throws ValidationException {
		String orchestratorNamePrefix = Run.ORCHESTRATOR_NAME + suffix
				+ RuntimeParameter.NODE_PROPERTY_SEPARATOR;

		assignRuntimeParameters(orchestratorNamePrefix);
		assignRuntimeParameter(orchestratorNamePrefix
				+ RuntimeParameter.HOSTNAME_KEY,
				RuntimeParameter.HOSTNAME_DESCRIPTION);
		assignRuntimeParameter(orchestratorNamePrefix
				+ RuntimeParameter.INSTANCE_ID_KEY,
				RuntimeParameter.INSTANCE_ID_DESCRIPTION);
	}

	/**
	 * @param prefix
	 *            Example (< nodename>.< index>:)
	 * @throws ValidationException
	 */
	public void assignRuntimeParameters(String prefix)
			throws ValidationException {
		assignRuntimeParameter(prefix + RuntimeParameter.STATE_KEY,
				Run.INITIAL_NODE_STATE, RuntimeParameter.STATE_DESCRIPTION);
		assignRuntimeParameter(prefix + RuntimeParameter.STATE_MESSAGE_KEY,
				Run.INITIAL_NODE_STATE,
				RuntimeParameter.STATE_MESSAGE_DESCRIPTION);
		assignRuntimeParameter(prefix + RuntimeParameter.STATE_CUSTOM_KEY, "",
				RuntimeParameter.STATE_CUSTOM_DESCRIPTION);
		assignRuntimeParameter(prefix + RuntimeParameter.STATE_VM_KEY, "",
				RuntimeParameter.STATE_VM_DESCRIPTION);
		assignRuntimeParameter(prefix + RuntimeParameter.ABORT_KEY, "",
				RuntimeParameter.ABORT_DESCRIPTION);
		assignRuntimeParameter(prefix + RuntimeParameter.COMPLETE_KEY, "false",
				RuntimeParameter.COMPLETE_DESCRIPTION);
		assignRuntimeParameter(prefix + RuntimeParameter.TAGS_KEY, "",
				RuntimeParameter.GLOBAL_TAGS_DESCRIPTION);
	}

	@Override
	public String getName() {
		return uuid;
	}

	@Override
	public void setName(String name) {
		this.uuid = name;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getModuleResourceUrl() {
		return moduleResourceUri;
	}

	public void setModuleResourceUrl(String moduleResourceUri) {
		this.moduleResourceUri = moduleResourceUri;
	}

	public Map<String, RuntimeParameter> getRuntimeParameters() {
		return runtimeParameters;
	}

	public void setRuntimeParameters(
			Map<String, RuntimeParameter> runtimeParameters) {
		this.runtimeParameters = runtimeParameters;
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
	}

	public String getRefqname() {
		return moduleResourceUri;
	}

	public void setRefqname(String refqname) {
		this.moduleResourceUri = refqname;
	}

	public void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	public Credentials getCredentials() {
		return this.credentials;
	}

	/**
	 * Set value to key, ignoring the abort flag, such that no exception is
	 * thrown.
	 * 
	 * @param key
	 * @return new value
	 * @throws AbortException
	 * @throws NotFoundException
	 */
	public String getRuntimeParameterValueIgnoreAbort(String key)
			throws NotFoundException {

		assert (runtimeParameters != null);

		RuntimeParameter parameter = extractRuntimeParameter(key);
		return parameter.getValue();
	}

	private RuntimeParameter extractRuntimeParameter(String key)
			throws NotFoundException {

		if (!runtimeParameters.containsKey(key)) {
			throwNotFoundException(key);
		}
		return runtimeParameters.get(key);
	}

	public String getRuntimeParameterValue(String key) throws AbortException,
			NotFoundException {

		if (isAbort()) {
			throw new AbortException("Abort flag raised!");
		}

		return getRuntimeParameterValueIgnoreAbort(key);
	}

	public void removeRuntimeParameter(String key) throws NotFoundException {

		assert (runtimeParameters != null);

		Metadata parameter = extractRuntimeParameter(key);
		runtimeParameters.remove(parameter);
	}

	public boolean isAbort() {
		RuntimeParameter abort = null;
		try {
			abort = extractRuntimeParameter(RuntimeParameter.GLOBAL_ABORT_KEY);
		} catch (NotFoundException e) {
		}
		return abort.isSet();
	}

	public void createRuntimeParameter(Node node, String key, String value)
			throws ValidationException {
		createRuntimeParameter(node, key, value, "", ParameterType.String);
	}

	public void createRuntimeParameter(Node node, String key, String value,
			String description, ParameterType type) throws ValidationException {

		// We only test for the first one
		String parameterName = composeParameterName(node, key, 1);
		if (getParameters().containsKey(parameterName)) {
			throw new ValidationException("Parameter " + parameterName
					+ " already exists in node " + node.getName());
		}

		for (int i = 1; i <= node.getMultiplicity(); i++) {
			assignRuntimeParameter(composeParameterName(node, key, i), value,
					description, type);
		}
	}

	private String composeParameterName(Node node, String key, int i) {
		return composeNodeName(node, i)
				+ RuntimeParameter.NODE_PROPERTY_SEPARATOR + key;
	}

	private String composeNodeName(Node node, int i) {
		return node.getName()
				+ RuntimeParameter.NODE_MULTIPLICITY_INDEX_SEPARATOR + i;
	}

	public Date getStart() {
		return (Date) startTime.clone();
	}

	public void setStart() {
		this.startTime = new Date();
	}

	public void setStart(Date start) {
		this.startTime = (Date) start.clone();
	}

	public Date getEnd() {
		return (Date) endTime.clone();
	}

	public void setEnd(Date end) {
		this.endTime = (Date) end.clone();
	}

	public void addNodeName(String node) {
		nodeNames += node + ", ";
	}

	/**
	 * Return nodenames, including a value for each index from 1 to multiplicity
	 * (e.g. apache1.1, apache1.2...)
	 * 
	 * @return comma separated nodenames
	 */
	public String getNodeNames() {
		return nodeNames;
	}

	public List<String> getNodeNameList() {
		return Arrays.asList(getNodeNames().split(", "));
	}

	@Override
	public String getResourceUri() {
		return resourceUri;
	}

	@Override
	public void setContainer(RunParameter parameter) {
		parameter.setContainer(this);
	}

	public String getUser() {
		return user_;
	}

	public void setUser(String user) {
		this.user_ = user;
	}

	public RuntimeParameter assignRuntimeParameter(String key, String value,
			String description) throws ValidationException {
		return assignRuntimeParameter(key, value, description,
				ParameterType.String);
	}

	public RuntimeParameter assignRuntimeParameter(String key, String value,
			String description, ParameterType type) throws ValidationException {
		if (runtimeParameters.containsKey(key)) {
			throw new ValidationException("Key " + key
					+ " already exists, cannot re-define");
		}
		RuntimeParameter parameter = new RuntimeParameter(this, key, value,
				description);

		parameter.setType(type);
		runtimeParameters.put(key, parameter);

		return parameter;
	}

	public RuntimeParameter assignRuntimeParameter(String key,
			String description) throws ValidationException {
		return assignRuntimeParameter(key, "", description);
	}

	public RuntimeParameter updateRuntimeParameter(String key, String value)
			throws NotFoundException, ValidationException {
		if (!runtimeParameters.containsKey(key)) {
			throwNotFoundException(key);
		}

		RuntimeParameter parameter = runtimeParameters.get(key);
		if (RuntimeParameter.GLOBAL_ABORT_KEY.equals(key)) {
			if (isAbort()) {
				return parameter;
			}
		}
		parameter.setValue(value);

		return getRuntimeParameters().get(key);
	}

	private void throwNotFoundException(String key) throws NotFoundException {
		throw new NotFoundException("Couldn't find key '" + key
				+ "' in execution instance: '" + getName() + "'");
	}

	public Run store() {
		return (Run) super.store();
	}

	public void setType(RunType type) {
		this.type = type;
	}

	public RunType getType() {
		return type;
	}

	public int getMultiplicity(String nodeName) throws NotFoundException {
		String multiplicity = getRuntimeParameterValueIgnoreAbort(nodeName
				+ RuntimeParameter.NODE_MULTIPLICITY_INDEX_SEPARATOR
				+ RuntimeParameter.MULTIPLICITY_NODE_START_INDEX
				+ RuntimeParameter.NODE_PROPERTY_SEPARATOR
				+ RuntimeParameter.MULTIPLICITY_PARAMETER_NAME);
		return Integer.parseInt(multiplicity);
	}

	public void setCloudServiceName(String cloudServiceName) {
		this.cloudServiceName = cloudServiceName;
	}

	public String getCloudServiceName() {
		return cloudServiceName;
	}

	// LS: Temporary method
	public Collection<Node> getNodes() throws ValidationException {
		// FIXME: this is a hack and needs a real fix
		if (module == null) {
			module = new RunDeploymentFactory().overloadModule(this,
					User.loadByName(getUser()));
		}

		if (module.getCategory() != ModuleCategory.Deployment) {
			throw new SlipStreamInternalException(
					"getNodes can only be used with a Deployment module");
		}

		Collection<Node> nodes = ((DeploymentModule) module).getNodes()
				.values();

		return nodes;
	}

	public HashSet<String> getCloudServicesList()
			throws ConfigurationException, ValidationException {
		HashSet<String> cloudServicesList = new HashSet<String>();
		for (Node n : this.getNodes()) {
			String cloudServiceName = n.getCloudService();
			cloudServicesList
					.add(getEffectiveCloudServiceName(cloudServiceName));
		}
		return cloudServicesList;
	}

	public String getEffectiveCloudServiceName(String cloudServiceName) {
		if (ConnectorFactory.isDefaultCloudService(cloudServiceName))
			cloudServiceName = this.cloudServiceName;

		return cloudServiceName;
	}

	public void addGroup(String group, String serviceName) {
		this.groups += serviceName + SERVICENAME_NODENAME_SEPARATOR + group
				+ ", ";
	}

	@Attribute
	@Lob
	public String getGroups() {
		getRuntimeParameters().get(RuntimeParameter.GLOBAL_NODE_GROUPS_KEY)
				.setValue(groups);
		return groups;
	}

	@Attribute
	@Lob
	public void setGroups(String groups) {
		this.groups = groups;
	}

	/**
	 * Populate a volatile module and override its parameter (e.g. cloud
	 * service, multiplicity)
	 * 
	 * @throws ValidationException
	 */
	private void populateModule() throws ValidationException {

		if (module.getCategory() == ModuleCategory.Deployment) {
			populateDeploymentModule((DeploymentModule) module);
		}
		if (module.getCategory() == ModuleCategory.Image) {
			populateImageModule((ImageModule) module);
		}
	}

	public List<String> getGroupNameList() {
		return Arrays.asList(getGroups().split(", "));
	}

	private void populateDeploymentModule(DeploymentModule deployment)
			throws ValidationException {
		for (Node node : deployment.getNodes().values()) {

			// node.setCloudService(getCloudServiceName());

			RunParameter runParameter;

			runParameter = getParameter(nodeRuntimeParameterKeyName(node,
					RuntimeParameter.MULTIPLICITY_PARAMETER_NAME));
			if (runParameter != null) {
				node.setMultiplicity(runParameter.getValue());
			}

			// runParameter = getParameter(nodeRuntimeParameterKeyName(node,
			// RuntimeParameter.CLOUD_SERVICE_NAME));
			// if (runParameter != null) {
			// node.setCloudService(runParameter.getValue());
			// }

			node.getImage().assignImageIdFromCloudService(
					node.getCloudService());
		}
	}

	public String nodeRuntimeParameterKeyName(Node node,
			String nodeParameterName) {
		return node.getName() + NODE_NAME_PARAMETER_SEPARATOR
				+ nodeParameterName;
	}

	private void populateImageModule(ImageModule image)
			throws ValidationException {
		if (type == RunType.Orchestration) {
			image.assignBaseImageIdToImageIdFromCloudService(getCloudServiceName());
		} else {
			image.assignImageIdFromCloudService(getCloudServiceName());
		}
	}

}
