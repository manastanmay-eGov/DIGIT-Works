import React, { useState, useEffect, Fragment, useRef }from 'react';
import { useTranslation } from "react-i18next";
import { useHistory } from 'react-router-dom';
import { Menu, Header, ActionBar, SubmitBar,ViewDetailsCard , HorizontalNav, Loader, WorkflowActions, Toast, MultiLink } from '@egovernments/digit-ui-react-components';


const ViewContractDetails = () => {
    const { t } = useTranslation();
    const history = useHistory();
    const [showActions, setShowActions] = useState(false);
    const [showToast, setShowToast] = useState(null);
    const menuRef = useRef();
    const queryStrings = Digit.Hooks.useQueryParams();
    const contractId = queryStrings?.workOrderNumber;
    const tenantId = Digit.ULBService.getCurrentTenantId();
    const businessService = Digit?.Customizations?.["commonUiConfig"]?.getBusinessService("contract")
    const [toast, setToast] = useState({show : false, label : "", error : false});
    const ContractSession = Digit.Hooks.useSessionStorage("CONTRACT_CREATE", {});
    const [sessionFormData, setSessionFormData, clearSessionFormData] = ContractSession;

    const loggedInUserRoles = Digit.Utils.getLoggedInUserDetails("roles");
    const [actionsMenu, setActionsMenu] = useState([]);

    const closeMenu = () => {
        setShowActions(false);
    }
    Digit.Hooks.useClickOutside(menuRef, closeMenu, showActions);

    const payload = {
        tenantId : queryStrings?.tenantId || tenantId,
        contractNumber : queryStrings?.workOrderNumber
    }
    const [cardState,setCardState] = useState([])
    const [activeLink, setActiveLink] = useState("Work_Order");
    const configNavItems = [
        {
            "name": "Work_Order",
            "code": "COMMON_WO_DETAILS",
            "active": true
        },
        {
            "name": "Terms_and_Conditions",
            "code": "COMMON_TERMS_&_CONDITIONS",
            "active": true
        }
    ]
    const ContractDetails = Digit.ComponentRegistryService.getComponent("ContractDetails");
    const TermsAndConditions = Digit.ComponentRegistryService.getComponent("TermsAndConditions");
    const {isLoading : isContractLoading, data, isError : isContractError, isSuccess, error} = Digit.Hooks.contracts.useViewContractDetails(payload?.tenantId, payload, {}, {cacheTime : 0})
    //const {isLoading : isContractLoading, data } = Digit.Hooks.contracts.useViewContractDetails(payload?.tenantId, payload, {})

    //fetching project data
    const { isLoading: isProjectLoading, data: project, isError : isProjectError } = Digit.Hooks.project.useProjectSearch({
        tenantId,
        searchParams: {
            Projects: [
                {
                    tenantId,
                    projectNumber : data?.applicationData?.additionalDetails?.projectId
                }
            ]
        },
        config:{
            enabled: !!(data?.applicationData?.additionalDetails?.projectId) 
        }
    })

    useEffect(() => {
        if (!window.location.href.includes("create-contract") && sessionFormData && Object.keys(sessionFormData) != 0) {
          clearSessionFormData();
        }
    }, [location]);

    useEffect(()=>{
        if(isContractError || (!isContractLoading && data?.isNoDataFound)) {
            setToast({show : true, label : t("COMMON_WO_NOT_FOUND"), error : true});
        }
    },[isContractError, data, isContractLoading]);

    useEffect(()=>{
        if(isProjectError) {
            setToast({show : true, label : t("COMMON_PROJECT_NOT_FOUND"), error : true});
        }
    },[isProjectError]);

    useEffect(() => {
        let isUserBillCreator = loggedInUserRoles?.includes("BILL_CREATOR");
        if (data?.applicationData?.wfStatus === "ACCEPTED" && data?.applicationData?.status === "ACTIVE" && isUserBillCreator){
            setActionsMenu((prevState => [...prevState,{
                name:"CREATE_PURCHASE_BILL"
            }]))
        }

    }, [data])

    const HandleDownloadPdf = () => {
        Digit.Utils.downloadEgovPDF('workOrder/work-order',{contractId,tenantId},`WorkOrder-${contractId}.pdf`)
    }

    const handleActionBar = (option) => {
        if (option?.name === "CREATE_PURCHASE_BILL") {
            history.push(`/${window.contextPath}/employee/expenditure/create-purchase-bill?tenantId=${tenantId}&workOrderNumber=${contractId}`);
        }
    }

    const handleToastClose = () => {
        setToast({show : false, label : "", error : false});
    }

    useEffect(() => {
        //here set cardstate when contract and project is available
        setCardState([
            {
                title: '',
                values: [
                  { title: "WORKS_ORDER_ID", value: payload?.contractNumber },
                  { title: "WORKS_PROJECT_ID", value: project?.projectNumber, },
                  { title: "ES_COMMON_PROPOSAL_DATE", value: Digit.DateUtils.ConvertEpochToDate(project?.additionalDetails?.dateOfProposal) },
                  { title: "ES_COMMON_PROJECT_NAME", value: project?.name },
                  { title: "PROJECTS_DESCRIPTION", value: project?.description }
                ]
              }
        ]) 
      }, [project])


    if(isProjectLoading || isContractLoading) 
         return <Loader/>;
    return (
      <React.Fragment>
        <div className={"employee-main-application-details"}>
          <div className={"employee-application-details"} style={{ marginBottom: "15px" }}>
            <Header className="works-header-view" styles={{ marginLeft: "0px", paddingTop: "10px"}}>{t("WORKS_VIEW_WORK_ORDER")}</Header>
            {(data?.applicationData?.wfStatus === "APPROVED" || data?.applicationData?.wfStatus === "PENDING_FOR_ACCEPTANCE" || data?.applicationData?.wfStatus === "ACCEPTED") && 
               <MultiLink
                 onHeadClick={() => HandleDownloadPdf()}
                 downloadBtnClassName={"employee-download-btn-className"}
                 label={t("CS_COMMON_DOWNLOAD")}
               />
            }
          </div>
          {project && <ViewDetailsCard cardState={cardState} t={t} />}
          {
            !data?.isNoDataFound && 
                <>
                    <HorizontalNav showNav={true} configNavItems={configNavItems} activeLink={activeLink} setActiveLink={setActiveLink} inFormComposer={false}>
                        {activeLink === "Work_Order" && <ContractDetails fromUrl={false} tenantId={tenantId} contractNumber={payload?.contractNumber} data={data} isLoading={isContractLoading}/>}
                        {activeLink === "Terms_and_Conditions" && <TermsAndConditions data={data?.applicationData?.additionalDetails?.termsAndConditions}/>}
                    </HorizontalNav>
                    <WorkflowActions
                        forcedActionPrefix={`WF_${businessService}_ACTION`}
                        businessService={businessService}
                        applicationNo={queryStrings?.workOrderNumber}
                        tenantId={tenantId}
                        applicationDetails={data?.applicationData}
                        url={Digit.Utils.Urls.contracts.update}
                        moduleCode="Contract"
                    />
                    {data?.applicationData?.wfStatus === "ACCEPTED" && data?.applicationData?.status === "ACTIVE" && actionsMenu?.length>0 ?
                        <ActionBar>

                            {showActions ? <Menu
                                localeKeyPrefix={`WF_CONTRACT_ACTION`}
                                options={actionsMenu}
                                optionKey={"name"}
                                t={t}
                                onSelect={handleActionBar}
                            />:null} 
                            <SubmitBar ref={menuRef} label={t("WORKS_ACTIONS")} onSubmit={() => setShowActions(!showActions)} />
                        </ActionBar>
                        : null
                    }
                </>
          }
        </div>
        {toast?.show && <Toast label={toast?.label} error={toast?.error} isDleteBtn={true} onClose={handleToastClose}></Toast>}
      </React.Fragment>
    );
}

export default ViewContractDetails



