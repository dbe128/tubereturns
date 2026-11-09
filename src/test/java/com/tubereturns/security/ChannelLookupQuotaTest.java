package com.tubereturns.security;

import com.tubereturns.dto.ChannelSearchResultDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChannelLookupQuotaTest extends ApiIntegrationTest {

    private static final int ANONYMOUS_SEARCHES_WITHIN_BUDGET = 19;

    @Test
    void anonymousCallerCannotResolveOrAssessChannels() throws Exception {
        mvc.perform(get("/api/channels/resolve").param("handle", "anything"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/channels/assess-relevance").param("handle", "anything").param("channelName", "Anything"))
                .andExpect(status().isForbidden());

        verify(youTubeApiService, never()).resolveChannelByHandle(anyString());
        verify(youTubeApiService, never()).getRecentVideoTitles(anyString(), anyInt());
    }

    @Test
    void anonymousSearchIsCachedAndBoundedByTheDailyBudget() throws Exception {
        when(youTubeApiService.searchChannels(anyString(), anyBoolean())).thenAnswer(invocation -> List.of(
                new ChannelSearchResultDto(invocation.getArgument(0), "Name", "https://www.youtube.com/@x",
                        null, null, null, null, null, "UC" + invocation.getArgument(0))));

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/channels/search").param("q", "  Repeated Query "))
                    .andExpect(status().isOk());
        }
        verify(youTubeApiService, times(1)).searchChannels(eq("repeated query"), anyBoolean());

        int lastStatus = 200;
        for (int i = 0; i < 40; i++) {
            lastStatus = mvc.perform(get("/api/channels/search").param("q", "distinct query " + i))
                    .andReturn().getResponse().getStatus();
        }
        assertThat(lastStatus).isEqualTo(429);
        verify(youTubeApiService, atMost(ANONYMOUS_SEARCHES_WITHIN_BUDGET)).searchChannels(anyString(), anyBoolean());

        mvc.perform(get("/api/channels/search").param("q", "repeated query"))
                .andExpect(status().isOk());
    }
}
