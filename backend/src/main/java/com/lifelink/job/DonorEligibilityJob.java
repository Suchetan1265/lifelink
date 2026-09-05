package com.lifelink.job;

import com.lifelink.donor.DonorEligibilityService;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Spec §2.E.4 — daily at 00:05, tell donors whose cooldown ended. */
public class DonorEligibilityJob extends QuartzJobBean {

    @Autowired
    private DonorEligibilityService donorEligibilityService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        donorEligibilityService.notifyNewlyEligibleDonors();
    }
}
