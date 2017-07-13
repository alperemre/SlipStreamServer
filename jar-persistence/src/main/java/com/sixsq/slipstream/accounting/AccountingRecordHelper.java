package com.sixsq.slipstream.accounting;

import com.sixsq.slipstream.event.ACL;
import com.sixsq.slipstream.event.TypePrincipal;
import com.sixsq.slipstream.event.TypePrincipalRight;

import com.sixsq.slipstream.exceptions.NotFoundException;
import com.sixsq.slipstream.persistence.Run;
import com.sixsq.slipstream.persistence.RuntimeParameter;
import com.sixsq.slipstream.util.ModuleUriUtil;
import com.sixsq.slipstream.util.SscljProxy;
import org.restlet.Response;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static com.sixsq.slipstream.event.TypePrincipal.PrincipalType.ROLE;
import static com.sixsq.slipstream.event.TypePrincipal.PrincipalType.USER;
import static com.sixsq.slipstream.event.TypePrincipalRight.Right.ALL;

/**
 * Created by elegoff on 10.07.17.
 */
public class AccountingRecordHelper {
    private Run run;
    private String nodeInstanceName;

    public AccountingRecordHelper(Run run, String nodeInstanceName) {
        this.run = run;
        this.nodeInstanceName = nodeInstanceName;
    }

    public Run getRun() {
        return run;
    }

    public String getNodeInstanceName() {
        return nodeInstanceName;
    }

    public static boolean isMuted = false;

    private static final Logger logger = Logger.getLogger(AccountingRecordHelper.class.getName());


    public ACL getACL() {
        String username = run.getUser();

        TypePrincipal owner = new TypePrincipal(USER, username);
        List<TypePrincipalRight> rules = Arrays.asList(
                new TypePrincipalRight(USER, username, ALL),
                new TypePrincipalRight(ROLE, "ADMIN", ALL));
        return new ACL(owner, rules);

    }

    public String getIdentifier() {
        return run.getUuid() + "/" + nodeInstanceName;
    }


    public String getCloudName() {
        String paramName = RuntimeParameter.constructParamName(nodeInstanceName, RuntimeParameter.CLOUD_SERVICE_NAME);
        return run.getRuntimeParameterValueOrDefaultIgnoreAbort(paramName, null);
    }

    public String getUser()  {
        return run.getUser();

    }

    public String getModuleName() {
        return ModuleUriUtil.extractModuleNameFromResourceUri(run.getModuleResourceUrl());
    }


    public String getServiceOffer() {
        String paramName = RuntimeParameter.constructParamName(nodeInstanceName, RuntimeParameter.SERVICE_OFFER);
        return run.getRuntimeParameterValueOrDefaultIgnoreAbort(paramName, null);
    }

    public AccountingRecordVM getVmData() {
        //FIXME
        return new AccountingRecordVM(1, 64, 1024);
    }

    public String getRealm() {
        //FIXME
        return "my-realm";
    }

    public List<String> getGroups() {
        //FIXME
        return null;
    }

    public List<String> getRoles() {
        //FIXME
        return null;
    }


    private static AccountingRecord getByIdentifier(String identifier, String username) {
        //FIXME
        String cimiQuery = "$filter=identifier='" + identifier + "'";

        String resource = null;
        try {
            resource = AccountingRecord.ACCOUNTING_RECORD_RESOURCE + "?" + URLEncoder.encode(cimiQuery, "UTF-8");
            Response res = SscljProxy.get(resource, username);

            AccountingRecords records = AccountingRecords.fromJson(res.getEntityAsText());

            if (records == null) return null;

            int nbRecords = records.getAccountingRecords().size();

            switch (nbRecords) {
                case 0: // FIXME :  no corresponding record was started , can't stop it
                    break;

                case 1: //happy case : a corresponding record was found in ES
                    AccountingRecord ar = records.getAccountingRecords().get(0);
                    if (ar.isOpenAndValidAccountingRecord()) {
                        // The record was properly started, and has not yet been closed
                        return ar;

                    } else {
                        //FIXME : the record we found is invalid
                    }
                    break;

                default: //FIXME : more than one record found, need to deal with that

            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void postStartAccountingRecord(Run run, String nodeInstanceName) {

        if (isMuted) {
            return;
        }

        AccountingRecordHelper helper = new AccountingRecordHelper(run, nodeInstanceName);

        ACL acl = helper.getACL();

        //FIXME : how would the Accounting record type be different from vm ?
        AccountingRecord.AccountingRecordType type = AccountingRecord.AccountingRecordType.vm;

        String identifier = helper.getIdentifier();
        String username = helper.getUser();

        String cloud = helper.getCloudName();

        List<String> roles = helper.getRoles();
        List<String> groups = helper.getGroups();
        String realm = helper.getRealm();
        String module = helper.getModuleName();
        String serviceOfferRef = helper.getServiceOffer();
        AccountingRecordVM vmData = helper.getVmData();

        AccountingRecord accountingRecord = new AccountingRecord(acl, type, identifier, new Date(), null, username, cloud, roles, groups, realm,
                module, serviceOfferRef, vmData.getCpu(), vmData.getRam(), vmData.getDisk());


        //Appending ' ADMIN' to get proper permissions
        String user = username + " ADMIN";
        AccountingRecord.add(accountingRecord, user);

    }

    public static void postStopAccountingRecord(Run run, String nodeInstanceName) {

        if (isMuted) {
            return;
        }

        AccountingRecordHelper helper = new AccountingRecordHelper(run, nodeInstanceName);
        String username = helper.getUser();
        String identifier = helper.getIdentifier();

        String user = username + " ADMIN";
        AccountingRecord ar = AccountingRecordHelper.getByIdentifier(identifier, user);


        //FIXME

        if (ar == null) {
            logger.warning("Could not find accounting record identified by " + identifier);
            return;
        }


        ar.setStop(new Date());
        AccountingRecord.edit(ar, user);

    }

    public static void muteForTests() {
        isMuted = true;
        logger.severe("You should NOT see this message in production: accounting records won't be posted");
    }


}
