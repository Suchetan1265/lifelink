package com.lifelink.job;

import com.lifelink.request.RequestMaintenanceService;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Spec §2.E.3 — every 15 minutes, expire requests past their needed-by time. */
public class RequestExpiryJob extends QuartzJobBean {

    @Autowired
    private RequestMaintenanceService requestMaintenanceService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        requestMaintenanceService.expirePastDueRequests();
    }
}
