package com.lifelink.job;

import com.lifelink.request.RequestMaintenanceService;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Spec §2.E.2 — every 15 minutes, escalate requests past their urgency deadline. */
public class RequestEscalationJob extends QuartzJobBean {

    // Field injection: Quartz constructs the job, Spring autowires it afterwards.
    @Autowired
    private RequestMaintenanceService requestMaintenanceService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        requestMaintenanceService.escalateOverdueRequests();
    }
}
