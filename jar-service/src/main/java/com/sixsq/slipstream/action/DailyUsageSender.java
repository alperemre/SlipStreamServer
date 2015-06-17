package com.sixsq.slipstream.action;

import com.sixsq.slipstream.action.usage.MailUsage;
import com.sixsq.slipstream.action.usage.MailsBuilder;
import com.sixsq.slipstream.action.usage.UsageSummaries;
import com.sixsq.slipstream.persistence.User;
import com.sixsq.slipstream.persistence.UserParameter;
import com.sixsq.slipstream.util.Notifier;
import org.restlet.Context;
import org.restlet.data.Parameter;
import org.restlet.engine.header.Header;
import org.restlet.resource.ClientResource;
import org.restlet.util.Series;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

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
public class DailyUsageSender {

    private static final String SSCLJ_SERVER = "http://localhost:8201/api";

    private static final Logger logger = Logger.getLogger(DailyUsageSender.class.getName());

    public static void main(String[] args) {
        sendDailyUsageEmails();
        System.exit(0);
    }

    public static void sendDailyUsageEmails() {

        logger.info("DailyUsageEmails will send emails");
        List<User> usersToEmail = usersFilteredByMailUsage(UserParameter.MAIL_USAGE_DAILY);
        if(usersToEmail == null || usersToEmail.isEmpty()){
            logger.info("No users to send daily email to. Returning.");
            return;
        }

        Map<String, String> nameEmails  = nameEmails(usersToEmail);
        Set<String> userNames           = nameEmails.keySet();
        logger.info("DailyUsageEmails usersToEmail = " + userNames);

        String response = getJsonYesterdayUsage(userNames);
        if(response == null) {
            logger.warning("No response from Usage service. Returning.");
            return;
        }

        UsageSummaries usageSummaries = UsageSummaries.fromJson(response);

        MailsBuilder mailsBuilder = new MailsBuilder(humanFormat(today()));
        List<MailUsage> mailUsages = mailsBuilder.buildMails(usageSummaries, nameEmails);

        for(MailUsage mailUsage : mailUsages){
            Notifier.sendHTMLNotification(mailUsage.to(), mailUsage.body());
            logger.info("Daily usage mail sent to " + mailUsage.to());
        }
    }


    private static String getJsonYesterdayUsage(Set<String> userNames) {
        try {

            Context context = new Context();
            Series<Parameter> parameters = context.getParameters();
            parameters.add("socketTimeout", "1000");
            parameters.add("idleTimeout", "1000");
            parameters.add("idleCheckInterval", "1000");
            parameters.add("socketConnectTimeoutMs", "1000");

            String uri = SSCLJ_SERVER + "/Usage?" + cimiQueryStringUsageYesterday(userNames);

            logger.info("DailyUsageEmails Will query Usage resource with uri = '" + uri + "'");

            ClientResource resource = new ClientResource(context, uri);

            resource.setRetryOnError(false);
            Series<Header> headers = (Series<Header>) resource.getRequestAttributes().get("org.restlet.http.headers");
            if (headers == null) {
                headers = new Series<Header>(Header.class);
                resource.getRequestAttributes().put("org.restlet.http.headers", headers);
            }
            headers.add("slipstream-authn-info", "super ADMIN");

            return resource.get().getText();

        } catch (Exception e) {
            logger.warning("Unable to getJsonYesterdayUsage :" + e.getMessage());
            return null;
        }
    }

    protected static String cimiQueryStringUsageYesterday(Set<String> userNames) {
        String yesterday = formatDate(yesterday());
        String today = formatDate(today());
        return cimiQueryStringUsage(userNames, yesterday, today);
    }

    protected static String cimiQueryStringUsage(Set<String> userNames, String start, String end) {

        if(userNames == null || userNames.isEmpty()){
            throw new IllegalArgumentException("No user names provided");
        }

        List<String> clauseUsers = new ArrayList<String>();
        for(String userName : userNames) {
            clauseUsers.add(String.format("user='%s'", userName));
        }
        String conditionUsers = String.join(" or ", clauseUsers);

        String queryString = String.format("$filter=start_timestamp=%s and end_timestamp=%s and (%s)",
                start, end, conditionUsers);
        return encodeQueryParameters(queryString);
    }

    protected static Date yesterday() {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        return yesterday.getTime();
    }

    protected static Date today() {
        return new Date();
    }

    private static String humanFormat(Date date) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(date);
    }

    protected static String formatDate(Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return simpleDateFormat.format(date);
    }

    private static List<User> usersFilteredByMailUsage(String mailUsage) {

        List<User> result = new ArrayList<User>();

        for (User u: User.list()){

            if(mailUsage.equals(u.getMailUsage())) {
                result.add(u);
            }
        }
        return result;
    }

    private static String encodeQueryParameter(String parameter) {

        int indexEquals = parameter.indexOf('=');
        if(indexEquals<0) {
            return parameter;
        }

        String key = parameter.substring(0, indexEquals);
        String value = parameter.substring(indexEquals+1);

        try {
            return URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException ignore) {
            ignore.printStackTrace();
            return null;
        }
    }

    protected static String encodeQueryParameters(String queryString) {
        String[] parameters = queryString.split("&");
        List<String> encodedParameters = new ArrayList<String>();
        for (String parameter : parameters) {
            encodedParameters.add(encodeQueryParameter(parameter));
        }
        return String.join("&", encodedParameters);
    }

    private static Map<String, String> nameEmails(List<User> users) {
        Map<String, String> result = new HashMap<String, String>();
        for(User user : users){
            result.put(user.getName(), user.getEmail());
        }
        return result;
    }

}
