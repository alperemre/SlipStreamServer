package com.sixsq.slipstream.accounting;

import com.sixsq.slipstream.event.ACL;
import com.sixsq.slipstream.util.SscljProxy;

import java.util.*;

public class AccountingRecord {

    protected static final String ACCOUNTING_RECORD_RESOURCE = "api/accounting-record";
    protected static final String ACCOUNTING_RECORD_URI = "http://sixsq.com/slipstream/1/AccountingRecord";

    @SuppressWarnings("unused")
    private String id;

    public String getId() {
        return id;
    }


    /**
     * to be Used only in unit tests
     *
     * @param d
     */
    public void setId(String id) {
        this.id = id;
    }

    @SuppressWarnings("unused")
    private ACL acl;

    @SuppressWarnings("unused")
    private String resourceURI;

    @SuppressWarnings("unused")
    private Date created;

    @SuppressWarnings("unused")
    private Date updated;


    /**
     * to be Used only in unit tests
     *
     * @param d
     */
    public void setUpdated(Date d) {
        updated = d;
    }

    @SuppressWarnings("unused")
    private AccountingRecordType type;


    @SuppressWarnings("unused")
    private Date start;

    public Date getStart() {
        return start;
    }


    @SuppressWarnings("unused")
    private Date stop;

    public Date getStop() {
        return stop;
    }

    @SuppressWarnings("unused")
    private String user;

    @SuppressWarnings("unused")
    private String cloud;

    @SuppressWarnings("unused")
    private List<String> roles;

    @SuppressWarnings("unused")
    private List<String> groups;

    @SuppressWarnings("unused")
    private String realm;

    @SuppressWarnings("unused")
    private String module;

    @SuppressWarnings("unused")
    private ServiceOfferRef serviceOffer;

    @SuppressWarnings("unused")
    private AccountingRecordContext context;

    public AccountingRecordContext getContext() {
        return context;
    }

    //Record Type VM
    @SuppressWarnings("unused")
    Integer cpu;

    //Record Type VM
    @SuppressWarnings("unused")
    Integer ram;

    //Record Type VM
    @SuppressWarnings("unused")
    Integer disk;

    @SuppressWarnings("unused")
    String instanceType;

    public long getDisk() {
        return disk;
    }

    public void setStop(Date stop) {
        this.stop = stop;
    }


    public AccountingRecord(ACL acl, AccountingRecordType type, Date start, Date stop, String user, String cloud, List<String> roles, List<String> groups,
                            String realm, String module, ServiceOfferRef serviceOfferRef, AccountingRecordContext context, Integer cpu, Integer ram, Integer disk, String instanceType) {
        this.acl = acl;
        this.resourceURI = ACCOUNTING_RECORD_URI;
        this.created = start;
        this.updated = new Date();
        this.type = type;
        this.start = start;
        this.stop = stop;
        this.user = user;
        this.cloud = cloud;
        this.roles = roles;
        this.groups = groups;
        this.realm = realm;
        this.module = module;
        this.serviceOffer = serviceOfferRef;
        this.context = context;
        this.cpu = cpu;
        this.ram = ram;
        this.disk = disk;
        this.instanceType = instanceType;

    }

    public static enum AccountingRecordType {
        //Virtual machine
        vm,
        //ObjectStore
        obj;
    }

    public String toJson() {
        return SscljProxy.toJson(this);
    }

    public static void open(AccountingRecord accountingRecord, String username) {
        SscljProxy.post(ACCOUNTING_RECORD_RESOURCE, username, accountingRecord);
    }

    public static void close(AccountingRecord accountingRecord, String username) {
        String uri = "api/" + accountingRecord.getId();
        SscljProxy.put(uri, username, new StopAccountingRecord());
    }

    /**
     * An open accounting record has a start timestamp but no stop timestamp yet
     *
     * @return true if it is open and valid
     */
    public boolean isOpenAndValidAccountingRecord() {

        String id = this.getId();
        if (id == null || id.isEmpty()) {
            return false;
        }

        Date start = this.getStart();
        Date stop = this.getStop();

        //Start date must exist and be in the past
        //Stop date must not be set yet
        return (start != null) && (stop == null);
    }


    /**
     * A closed accounting record has both a start and stop timestamp (stop has to be after start)
     *
     * @return true if is closed and valid
     */
    public boolean isClosedAndValidAccountingRecord() {

        if (id == null || id.isEmpty()) {
            return false;
        }
        Date start = this.getStart();
        Date stop = this.getStop();

        //Start date must exist and be in the past
        //Stop date must not be set yet
        return (start != null) && (stop != null) && (start.before(stop));
    }

}
