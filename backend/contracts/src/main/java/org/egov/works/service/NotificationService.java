package org.egov.works.service;

import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.RequestInfoWrapper;
import digit.models.coremodels.SMSRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.works.config.ContractServiceConfiguration;
import org.egov.works.kafka.Producer;
import org.egov.works.repository.ServiceRequestRepository;
import org.egov.works.util.*;
import org.egov.works.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationService {

    @Autowired
    private Producer producer;

    @Autowired
    private ServiceRequestRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ContractServiceConfiguration config;

    @Autowired
    private HRMSUtils hrmsUtils;

    @Autowired
    private EstimateServiceUtil estimateServiceUtil;

    @Autowired
    private ProjectServiceUtil projectServiceUtil;

    @Autowired
    private LocationServiceUtil locationServiceUtil;

    @Autowired
    private OrgUtils organisationServiceUtil;


    /**
     * Sends notification by putting the sms content onto the core-sms topic
     *
     * @param request
     */
    public void sendNotification(ContractRequest request) {
        Workflow workflow = request.getWorkflow();

        if ("REJECT".equalsIgnoreCase(workflow.getAction())) {
            pushNotificationToCreatorForRejectAction(request);
        } else if ("APPROVE".equalsIgnoreCase(workflow.getAction())) {
            //No template present for Creator Approve Action
            pushNotificationToCreatorForApproveAction(request);
            pushNotificationToCBOForApproveAction(request);
        }
        else if ("ACCEPT".equalsIgnoreCase(workflow.getAction())) {
            pushNotificationToCreatorForAcceptAction(request);
        }
        else if ("DECLINE".equalsIgnoreCase(workflow.getAction())) {
            pushNotificationToCreatorForDeclineAction(request);
        }

    }

    private void pushNotificationToCreatorForRejectAction(ContractRequest request) {
        Contract contract = request.getContract();
        String createdByUuid = request.getContract().getAuditDetails().getCreatedBy();

        log.info("get message template for reject action");
        String message = getMessage(request, false);

        if (StringUtils.isEmpty(message)) {
            log.info("SMS content has not been configured for this case");
            return;
        }

        //get project number, location, userDetails
        log.info("get project number, location, userDetails");
        Map<String, String> smsDetails = getDetailsForSMS(request, createdByUuid);
        Map<String, Object> additionalField = setAdditionalFields(request,ContractServiceConstants.CONTRACTS_REJECT_LOCALIZATION_CODE);

        log.info("build Message For Reject Action");
        message = buildMessageForRejectAction(contract, smsDetails, message);
        log.info("push message for REJECT Action");
        checkAdditionalFieldAndPushONSmsTopic(message,additionalField,smsDetails);

    }

    private void pushNotificationToCreatorForApproveAction(ContractRequest request) {
        Contract contract = request.getContract();
        String createdByUuid = request.getContract().getAuditDetails().getCreatedBy();

        log.info("get message template of creator for approve action");
        String message = getMessage(request, false);

        if (StringUtils.isEmpty(message)) {
            log.info("SMS content has not been configured for this case");
            return;
        }

        //get project number, location, userDetails
        log.info("get project number, location, userDetails");
        Map<String, String> smsDetails = getDetailsForSMS(request, createdByUuid);
        Map<String, Object> additionalField = setAdditionalFields(request,ContractServiceConstants.CONTRACTS_APPROVE_CREATOR_LOCALIZATION_CODE);


        log.info("build Message For Approve Action for WO Creator");
        message = buildMessageForApproveAction_WOCreator(contract, smsDetails, message);

        log.info("push Message For Approve Action for WO Creator");
        checkAdditionalFieldAndPushONSmsTopic(message,additionalField,smsDetails);

    }

    private void pushNotificationToCreatorForDeclineAction(ContractRequest request) {
        Contract contract = request.getContract();
        String createdByUuid = request.getContract().getAuditDetails().getCreatedBy();

        log.info("get message template of creator for decline action");
        String message = getMessage(request, false);

        if (StringUtils.isEmpty(message)) {
            log.info("SMS content has not been configured for this case");
            return;
        }

        //get project number, location, userDetails
        log.info("get project number, location, userDetails");
        Map<String, String> smsDetails = getDetailsForSMS(request, createdByUuid);
        Map<String, Object> additionalField = setAdditionalFields(request,ContractServiceConstants.CONTRACTS_DECLINE_CREATOR_LOCALIZATION_CODE);


        log.info("build Message For decline Action for WO Creator");
        message = buildMessageForDeclineAction_WOCreator(contract, smsDetails, message);
        log.info("push Message For decline Action for WO Creator");
        checkAdditionalFieldAndPushONSmsTopic(message,additionalField,smsDetails);


    }

    private void pushNotificationToCreatorForAcceptAction(ContractRequest request) {
        Contract contract = request.getContract();
        String createdByUuid = request.getContract().getAuditDetails().getCreatedBy();

        log.info("get message template of creator for Accept action");
        String message = getMessage(request, false);

        if (StringUtils.isEmpty(message)) {
            log.info("SMS content has not been configured for this case");
            return;
        }

        //get project number, location, userDetails
        log.info("get project number, location, userDetails");
        Map<String, String> smsDetails = getDetailsForSMS(request, createdByUuid);
        Map<String, Object> additionalField = setAdditionalFields(request,ContractServiceConstants.CONTRACTS_ACCEPT_CREATOR_LOCALIZATION_CODE);

        log.info("build Message For Accept Action for WO Creator");
        message = buildMessageForAcceptAction_WOCreator(contract, smsDetails, message);
        log.info("push Message For Accept Action for WO Creator");
        checkAdditionalFieldAndPushONSmsTopic(message,additionalField,smsDetails);
    }

    private void pushNotificationToCBOForApproveAction(ContractRequest request) {
        Contract contract = request.getContract();
        String message = getMessage(request, true);

        if (StringUtils.isEmpty(message)) {
            log.info("SMS content has not been configured for this case");
            return;
        }

        //get org-details: orgName, contactPersonNames, mobileNumbers
        //Map<String, List<String>> orgDetails = getOrgDetailsForCBOAdmin(request);

        Map<String, List<String>> projDetails = getProjectNumber(request);
        Map<String, Object> additionalField = setAdditionalFields(request,ContractServiceConstants.CONTRACTS_APPROVE_CBO_LOCALIZATION_CODE);



        for (int i = 0; i < projDetails.get("mobileNumbers").size(); i++) {
            Map<String,String> smsDetails=new HashMap<>();
            smsDetails.put("projectId",projDetails.get("projectId").get(0));
            smsDetails.put("mobileNumber",projDetails.get("mobileNumbers").get(i));
            String customizedMessage = buildMessageForApproveAction_WO_CBO(contract, smsDetails, message);
            checkAdditionalFieldAndPushONSmsTopic(customizedMessage,additionalField,smsDetails);

        }
    }

    private Map<String,Object> setAdditionalFields(ContractRequest request, String localizationCode){
        Map<String, Object> additionalField=new HashMap<>();
        String tenantId = request.getContract().getTenantId();
        String rootTenantId = tenantId.split("\\.")[0];
        System.out.print("tenantId::::::"+ rootTenantId);
        if(rootTenantId.equalsIgnoreCase("od")){
            additionalField.put("templateCode",localizationCode);
            additionalField.put("requestInfo",request.getRequestInfo());
            additionalField.put("tenantId",request.getContract().getTenantId());
        }
        return additionalField;
    }

    private void checkAdditionalFieldAndPushONSmsTopic( String customizedMessage , Map<String, Object> additionalField,Map<String,String> smsDetails){


        if(!additionalField.isEmpty()){
            WorksSmsRequest smsRequest=WorksSmsRequest.builder().message(customizedMessage).additionalFields(additionalField)
                    .mobileNumber(smsDetails.get("mobileNumber")).build();


            System.out.println("SMS message:::::" + smsRequest.toString());
            log.info("SMS message:::::" + smsRequest.toString());
            producer.push(config.getMuktaNotificationTopic(), smsRequest);

        }else{
            SMSRequest smsRequest = SMSRequest.builder().mobileNumber(smsDetails.get("mobileNumber")).message(customizedMessage).build();
            System.out.println("SMS message without additional fields:::::" + smsRequest.toString());
            producer.push(config.getSmsNotifTopic(), smsRequest);
        }
    }

    private Map<String, String> getDetailsForSMS(ContractRequest request, String uuid) {
        RequestInfo requestInfo = request.getRequestInfo();
        Contract contract = request.getContract();
        String tenantId = contract.getTenantId();

        Map<String, String> smsDetails = new HashMap<>();


        //fetch the logged in user's mobileNumber, username, designation
        Map<String, String> userDetailsForSMS = hrmsUtils.
                getEmployeeDetailsByUuid(requestInfo, tenantId, uuid);


        // fetch project details - project name and location
        List<LineItems> lineItems = request.getContract().getLineItems();
        Map<String, List<LineItems>> lineItemsMap = lineItems.stream().collect(Collectors.groupingBy(LineItems::getEstimateId));
        List<Estimate> estimates = estimateServiceUtil.fetchActiveEstimates(requestInfo, tenantId, lineItemsMap.keySet());
        Map<String, String> projectDetails = projectServiceUtil.getProjectDetails(requestInfo, estimates.get(0));

        //As the new template only requires the project id so fetching it in this class only rather than calling the util method
        String projectId = estimates.get(0).getProjectId();

        //get location name from boundary type
       /* String boundaryCode = projectDetails.get("boundary");
        String boundaryType=projectDetails.get("boundaryType");
        Map<String, String> locationName = locationServiceUtil.getLocationName(tenantId, requestInfo, boundaryCode, boundaryType);*/

        //get org name
        Map<String, List<String>> orgDetails = organisationServiceUtil.getOrganisationInfo(request);

        smsDetails.put("orgName",orgDetails.get("orgName").get(0));
        smsDetails.putAll(userDetailsForSMS);
        smsDetails.put("projectId",projectDetails.get("projectNumber"));

       /* smsDetails.putAll(projectDetails);
        smsDetails.putAll(locationName);*/

        return smsDetails;
    }

    private Map<String, List<String>> getProjectNumber(ContractRequest request) {

        RequestInfo requestInfo = request.getRequestInfo();
        Contract contract = request.getContract();
        String tenantId = contract.getTenantId();

        // fetch project details - project name and location
        List<LineItems> lineItems = request.getContract().getLineItems();
        Map<String, List<LineItems>> lineItemsMap = lineItems.stream().collect(Collectors.groupingBy(LineItems::getEstimateId));
        List<Estimate> estimates = estimateServiceUtil.fetchActiveEstimates(requestInfo, tenantId, lineItemsMap.keySet());

        //As the new template only requires the project id so fetching it in this class only rather than calling the util method
         Map<String, String> projectDetails = projectServiceUtil.getProjectDetails(requestInfo, estimates.get(0));

        // Fetching org mobile number and maintaining in the map
        Map<String,List<String>> projectAndOrgDetails= organisationServiceUtil.getOrganisationInfo(request);

//        orgDetails.put("projectName", Collections.singletonList(projectDetails.get("projectName")));

        projectAndOrgDetails.put("projectId",Collections.singletonList(projectDetails.get("projectNumber")));

        return projectAndOrgDetails;
    }


    private Map<String, List<String>> getOrgDetailsForCBOAdmin(ContractRequest request) {

        RequestInfo requestInfo = request.getRequestInfo();
        Contract contract = request.getContract();
        String tenantId = contract.getTenantId();

        // fetch project details - project name and location
        List<LineItems> lineItems = request.getContract().getLineItems();
        Map<String, List<LineItems>> lineItemsMap = lineItems.stream().collect(Collectors.groupingBy(LineItems::getEstimateId));
        List<Estimate> estimates = estimateServiceUtil.fetchActiveEstimates(requestInfo, tenantId, lineItemsMap.keySet());
        Map<String, String> projectDetails = projectServiceUtil.getProjectDetails(requestInfo, estimates.get(0));

        Map<String,List<String>> orgDetails=organisationServiceUtil.getOrganisationInfo(request);
        orgDetails.put("projectName", Collections.singletonList(projectDetails.get("projectName")));

        return orgDetails;
    }


    private String getMessage(ContractRequest request, boolean isCBORole) {

        Workflow workflow = request.getWorkflow();
        String message = null;

        if ("REJECT".equalsIgnoreCase(workflow.getAction()) && !isCBORole) {
            message = getMessage(request, ContractServiceConstants.CONTRACTS_REJECT_LOCALIZATION_CODE);
        } else if ("APPROVE".equalsIgnoreCase(workflow.getAction()) && !isCBORole) {
            message = getMessage(request, ContractServiceConstants.CONTRACTS_APPROVE_CREATOR_LOCALIZATION_CODE);
        } else if ("APPROVE".equalsIgnoreCase(workflow.getAction()) && isCBORole) {
            message = getMessage(request, ContractServiceConstants.CONTRACTS_APPROVE_CBO_LOCALIZATION_CODE);
        }else if ("ACCEPT".equalsIgnoreCase(workflow.getAction()) && !isCBORole) {
            message = getMessage(request, ContractServiceConstants.CONTRACTS_ACCEPT_CREATOR_LOCALIZATION_CODE);
        }else if ("DECLINE".equalsIgnoreCase(workflow.getAction()) && !isCBORole) {
            message = getMessage(request, ContractServiceConstants.CONTRACTS_DECLINE_CREATOR_LOCALIZATION_CODE);
        }

        return message;
    }

    /**
     * Gets the message from localization
     *
     * @param request
     * @param msgCode
     * @return
     */
    public String getMessage(ContractRequest request, String msgCode) {
        String rootTenantId = request.getContract().getTenantId().split("\\.")[0];
        String locale=request.getRequestInfo().getMsgId().split("\\|")[1];
        Map<String, Map<String, String>> localizedMessageMap = getLocalisedMessages(request.getRequestInfo(), rootTenantId,
                locale, ContractServiceConstants.CONTRACTS_MODULE_CODE);
        return localizedMessageMap.get(locale+ "|" + rootTenantId).get(msgCode);
    }

    /**
     * Builds msg based on the format
     *
     * @param contract
     * @param message
     * @param userDetailsForSMS
     * @return
     */
    public String buildMessageForRejectAction(Contract contract, Map<String, String> userDetailsForSMS, String message) {
        /*message = message.replace("{CONTRACT_NUMBER}", contract.getContractNumber())
                .replace("{PROJECT_NAME}", userDetailsForSMS.get("projectName"))
                .replace("{LOCATION}", userDetailsForSMS.get("locationName"))
                .replace("{USERNAME}", userDetailsForSMS.get("userName"))
                .replace("{DESIGNATION}", userDetailsForSMS.get("designation"));*/

        message = message.replace("{workorderno} ", contract.getContractNumber())
                .replace("{projectid}", userDetailsForSMS.get("projectId"));

        return message;
    }

    public String buildMessageForApproveAction_WO_CBO(Contract contract, Map<String, String> userDetailsForSMS, String message) {

      /* long dueDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(new Integer(config.getContractDueDatePeriod()));

       StringBuilder CBOUrl= new StringBuilder(config.getCboUrlHost()).append(config.getCboUrlEndpoint());
       String shortendURL = getShortnerURL(CBOUrl.toString());

        DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dueDate);
        String date = formatter.format(calendar.getTime());*/

        message = message.replace("{projectid}", userDetailsForSMS.get("projectId"))
                .replace("{cborole}", contract.getExecutingAuthority());
        return message;
    }

    public String buildMessageForApproveAction_WOCreator(Contract contract, Map<String, String> userDetailsForSMS, String message) {
        message = message.replace("{CONTRACT_NUMBER}", contract.getContractNumber())
                .replace("{PROJECT_NAME}", userDetailsForSMS.get("projectName"))
                .replace("{LOCATION}", userDetailsForSMS.get("locationName"))
                .replace("{organisationName}", userDetailsForSMS.get("orgName"));
        return message;
    }

    public String buildMessageForDeclineAction_WOCreator(Contract contract, Map<String, String> userDetailsForSMS, String message) {
        message = message.replace("{workorderno}", contract.getContractNumber())
                .replace("{projectid}", userDetailsForSMS.get("projectId"));
        return message;
    }

    public String buildMessageForAcceptAction_WOCreator(Contract contract, Map<String, String> userDetailsForSMS, String message) {
        message = message.replace("{workorderno}", contract.getContractNumber())
                .replace("{projectid}", userDetailsForSMS.get("projectId"));
        return message;
    }

    /**
     * Creates a cache for localization that gets refreshed at every call.
     *
     * @param requestInfo
     * @param rootTenantId
     * @param locale
     * @param module
     * @return
     */
    public Map<String, Map<String, String>> getLocalisedMessages(RequestInfo requestInfo, String rootTenantId, String locale, String module) {
        Map<String, Map<String, String>> localizedMessageMap = new HashMap<>();
        Map<String, String> mapOfCodesAndMessages = new HashMap<>();
        StringBuilder uri = new StringBuilder();
        RequestInfoWrapper requestInfoWrapper = new RequestInfoWrapper();
        requestInfoWrapper.setRequestInfo(requestInfo);
        uri.append(config.getLocalizationHost()).append(config.getLocalizationContextPath())
                .append(config.getLocalizationSearchEndpoint()).append("?tenantId=" + rootTenantId)
                .append("&module=" + module).append("&locale=" + locale);
        List<String> codes = null;
        List<String> messages = null;
        Object result = null;
        try {
            result = repository.fetchResult(uri, requestInfoWrapper);
            codes = JsonPath.read(result, ContractServiceConstants.CONTRACTS_LOCALIZATION_CODES_JSONPATH);
            messages = JsonPath.read(result, ContractServiceConstants.CONTRACTS_LOCALIZATION_MSGS_JSONPATH);
        } catch (Exception e) {
            log.error("Exception while fetching from localization: " + e);
        }
        if (null != result) {
            for (int i = 0; i < codes.size(); i++) {
                mapOfCodesAndMessages.put(codes.get(i), messages.get(i));
            }
            localizedMessageMap.put(locale + "|" + rootTenantId, mapOfCodesAndMessages);
        }

        return localizedMessageMap;
    }

    /**
     *
     * @param actualURL Actual URL
     * @return Shortened URL
     */
    public String getShortnerURL(String actualURL) {
        HashMap<String,String> body = new HashMap<>();
        body.put("url",actualURL);
        StringBuilder builder = new StringBuilder(config.getUrlShortnerHost());
        builder.append(config.getUrlShortnerEndpoint());
        String res = restTemplate.postForObject(builder.toString(), body, String.class);

        if(StringUtils.isEmpty(res)){
            log.error("URL_SHORTENING_ERROR","Unable to shorten url: "+actualURL); ;
            return actualURL;
        }
        else return res;
    }

}
