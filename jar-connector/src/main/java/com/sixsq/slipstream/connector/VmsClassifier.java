package com.sixsq.slipstream.connector;

import com.sixsq.slipstream.persistence.Vm;
import java.util.logging.Logger;

import java.util.*;

public class VmsClassifier {

    public static final String CLOUD_VM = "CLOUD_VM";
    public static final String DB_VM = "DB_VM";

    private static java.util.logging.Logger logger = Logger.getLogger(VmsClassifier.class.getName());

    private  Map<String, Vm> newVmsMap = new HashMap<String, Vm>();
    private  Map<String, Vm> goneVmsMap = new HashMap<String, Vm>();

    // {id -> {VmKind -> Vm}}
    // VmKind can be either CLOUD_VM or DB_VM
    private  Map<String, Map<String, Vm>> stayingVmsMap = new HashMap<String, Map<String, Vm>>();

    public VmsClassifier(List<Vm> cloudVms, List<Vm> dbVms) {
        classify(cloudVms, dbVms);
    }

    private void classify(List<Vm> cloudVms, List<Vm> dbVms) {

        Map<String, Vm> cloudVmsMap = toMapByInstanceId(cloudVms);
        Map<String, Vm> dbVmsMap    = toMapByInstanceId(dbVms);

        Set<String> allInstanceIds = new HashSet<String>(cloudVmsMap.keySet());
        allInstanceIds.addAll(dbVmsMap.keySet());

        for (String id : allInstanceIds) {
            boolean inCloud = cloudVmsMap.containsKey(id);
            boolean inDb    = dbVmsMap.containsKey(id);

            if (inCloud && inDb) {
                // Arrays.asList(dbVmsMap.get(id), cloudVmsMap.get(id))
                Map<String, Vm> dbAndCloud = new HashMap<String, Vm>();
                dbAndCloud.put(CLOUD_VM, cloudVmsMap.get(id));
                dbAndCloud.put(DB_VM, dbVmsMap.get(id));
                stayingVmsMap.put(id, dbAndCloud);

            } else if (inCloud) {
                newVmsMap.put(id, cloudVmsMap.get(id));

            } else if (inDb) {
                goneVmsMap.put(id, dbVmsMap.get(id));

            } else {
                logger.warning("Unable to classify Vm:" + id);
            }
        }

        logger.info("Classify done");
        logger.info("Classify, nb NEW Vms     = " + newVms().size());
        logger.info("Classify, nb GONE Vms    = " + goneVms().size());
        logger.info("Classify, nb STAYING Vms = " + stayingVmsMap.keySet().size());
    }

    public Collection<Vm> goneVms(){
        return goneVmsMap.values();
    }

    public Collection<Vm> newVms(){
        return newVmsMap.values();
    }

    public Set<Map.Entry<String, Map<String, Vm>>> stayingVms(){
        return stayingVmsMap.entrySet();
    }

    /**
     * This method assumes that the input VMs correspond to a single cloud.
     * Otherwise, duplicate instance ids would overwrite each other.
     *
     * @param vms
     *            for a single cloud
     * @return mapped VMs by instance id
     */
    private static Map<String, Vm> toMapByInstanceId(List<Vm> vms) {
        Map<String, Vm> map = new HashMap<String, Vm>();
        for (Vm v : vms) {
            map.put(v.getInstanceId(), v);
        }
        return map;
    }

}

