package com.sixsq.slipstream.connector;

import com.sixsq.slipstream.acl.ACL;
import com.sixsq.slipstream.acl.TypePrincipal;
import com.sixsq.slipstream.acl.TypePrincipalRight;
import com.sixsq.slipstream.persistence.VirtualMachine;
import com.sixsq.slipstream.persistence.VirtualMachines;
import com.sixsq.slipstream.persistence.Vm;
import com.sixsq.slipstream.util.SscljProxy;
import org.restlet.Response;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.logging.Logger;

import static com.sixsq.slipstream.util.SscljProxy.BASE_RESOURCE;
import static com.sixsq.slipstream.acl.TypePrincipal.PrincipalType.USER;
import static com.sixsq.slipstream.acl.TypePrincipalRight.Right.VIEW;

public class VirtualMachineHandler {
    protected static final String VIRTUAL_MACHINE_RESOURCE = BASE_RESOURCE + "virtual-machine";
    private static final String USERNAME = "internal";
    private static final String ROLE = "ADMIN";
    private static final String USERNAME_ROLE = USERNAME + " " + ROLE;
    private static final Logger logger = Logger.getLogger(VirtualMachineHandler.class.getName());

    public static void removeVM(Vm vm) {
        VirtualMachine vmDb = fetchVirtualMachine(vm.getCloud(), vm.getInstanceId());
        if (vmDb == null) return; //nothing to remove

        delete(vmDb.getId());
    }

    private static void add(VirtualMachine virtualMachine) {
        SscljProxy.post(VIRTUAL_MACHINE_RESOURCE, USERNAME_ROLE, virtualMachine);
    }

    private static void update(VirtualMachine virtualMachine, String id) {
        SscljProxy.put(BASE_RESOURCE + id, USERNAME_ROLE, virtualMachine);
    }

    private static void delete(String id) {
        SscljProxy.delete(BASE_RESOURCE + id, USERNAME_ROLE);
    }

    public static void handleVM(Vm vm) {
        VirtualMachine vmRecord = VirtualMachineHandler.getResourceFromPojo(vm);
        VirtualMachine vmDb = fetchVirtualMachine(vm.getCloud(), vm.getInstanceId());

        ACL acl = (vmDb != null) ? vmDb.getAcl() : new ACL(new TypePrincipal(USER, USERNAME));

        // TODO : update code below when the credential resource is available

        // Update ACL so that any runOwner can view the resource
        String runOwner = vm.getRunOwner();
        if (runOwner != null) {
            acl.addRule(new TypePrincipalRight(USER, runOwner, VIEW));
        }

        // Update ACL so that the user who did the describe can view the resource
        String user = vm.getUser();
        if (user != null) {
            acl.addRule(new TypePrincipalRight(USER, user, VIEW));
        }

        // ACL may have been updated
        vmRecord.setAcl(acl);

        if (vmDb != null) {
            for (VirtualMachine.CredentialRef credential : vmDb.getCredentials()) {
                vmRecord.addCredential(credential);
            }
        }

        if (vmDb == null) {
            add(vmRecord);
        } else {
            update(vmRecord, vmDb.getId());
        }
    }

    //Identify a VirtualMachine document in ES from its cloud and instanceID
    public static VirtualMachine fetchVirtualMachine(String cloud, String instanceID) {

        String cimiQuery = new StringBuffer()
                .append("connector/href='connector/").append(cloud)
                .append("' and instanceID='").append(instanceID).append("'").toString();
        VirtualMachine virtualMachine = null;

        try {
            String resource = VIRTUAL_MACHINE_RESOURCE + "?$filter=" + URLEncoder.encode(cimiQuery, "UTF-8");
            Response res = SscljProxy.get(resource, " internal ADMIN");

            if (res == null) return null;

            VirtualMachines records = VirtualMachines.fromJson(res.getEntityAsText());

            if (records == null) return null;

            List<VirtualMachine> machines = records.getVirtualMachines();
            int nbRecords = machines.size();

            switch (nbRecords) {
                case 0: //  no corresponding record was found
                    logger.warning("Loading ressource with Query " + resource + " did not return any record");
                    break;

                case 1: //happy case : a corresponding record was found in ES
                    virtualMachine = machines.get(0);
                    logger.finest("Found record: " + SscljProxy.toJson(virtualMachine));
                    break;

                default:
                    // more than one record found, we expect to identify a single document

                    logger.warning("Loading ressource with Query " + resource + " did return too many (" + nbRecords + ") records");
                    for (VirtualMachine vmToDelete : machines) {
                        delete(vmToDelete.getId());
                    }
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return virtualMachine;

    }

    private static VirtualMachine getResourceFromPojo(Vm vm) {
        VirtualMachine resource = new VirtualMachine();

        resource.setInstanceID(vm.getInstanceId());
        resource.setConnector(new VirtualMachine.CloudRef(vm.getCloud()));
        resource.setState(vm.getState());
        resource.setIp(vm.getIp());

        VirtualMachine.UserRef userHref = new VirtualMachine.UserRef(vm.getUser());
        resource.addCredential(new VirtualMachine.CredentialRef(userHref.href));

        //the VM may have a runUuid which will be the run reference of the Virtual Machine CIMI resource
        if (vm.getRunUuid() != null) {

            String runOwner = vm.getRunOwner();
            VirtualMachine.UserRef userRef = new VirtualMachine.UserRef(runOwner);

            String runHref = "run/" + vm.getRunUuid();
            VirtualMachine.DeploymentRef runRef = new VirtualMachine.DeploymentRef(runHref, userRef);
            resource.setDeployment(runRef);

        }

        return resource;
    }

}
