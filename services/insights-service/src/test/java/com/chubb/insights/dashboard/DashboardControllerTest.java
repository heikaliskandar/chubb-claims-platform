package com.chubb.insights.dashboard;

import com.chubb.insights.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class, properties = "app.security.demo-users.password=test-password")
@Import(SecurityConfig.class)
class DashboardControllerTest {

    private static final String PASSWORD = "test-password";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboards;

    @Test
    void exposureIsForManagersOnly() throws Exception {
        given(dashboards.exposure()).willReturn(new ExposureReport(null, List.of(), List.of()));

        mvc.perform(get("/api/v1/insights/exposure").with(httpBasic("manager1", PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/insights/exposure").with(httpBasic("officer1", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void workloadIsForOfficersAndManagersButNotClaimants() throws Exception {
        given(dashboards.workload()).willReturn(new WorkloadReport(null, 0, List.of()));

        mvc.perform(get("/api/v1/insights/workload").with(httpBasic("officer1", PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/insights/workload").with(httpBasic("manager1", PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/insights/workload").with(httpBasic("claimant1", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/insights/performance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
