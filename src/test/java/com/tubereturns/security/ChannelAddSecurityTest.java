package com.tubereturns.security;

import com.tubereturns.dto.ChannelSearchResultDto;
import com.tubereturns.model.Channel;
import com.tubereturns.repository.ChannelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChannelAddSecurityTest extends ApiIntegrationTest {

    @Autowired
    ChannelRepository channelRepository;

    @Test
    void freeUserCannotOverwriteAnExistingChannel() throws Exception {
        Channel existing = channelRepository.findAll().getFirst();
        String originalName = existing.getChannelName();
        String originalDescription = existing.getDescription();

        mvc.perform(post("/api/channels/{handle}/add", existing.getHandle())
                        .header("Authorization", bearer("free-overwrite@example.test", "FREE"))
                        .param("channelName", "Defaced")
                        .param("description", "Defaced description")
                        .param("subscriberCount", "1"))
                .andExpect(status().isBadRequest());

        Channel after = channelRepository.findByHandle(existing.getHandle()).orElseThrow();
        assertThat(after.getChannelName()).isEqualTo(originalName);
        assertThat(after.getDescription()).isEqualTo(originalDescription);
        verify(youTubeApiService, never()).resolveChannelByHandle(existing.getHandle());
    }

    @Test
    void freeUserCannotCreateAChannelYouTubeDoesNotKnow() throws Exception {
        when(youTubeApiService.resolveChannelByHandle("ghost-channel")).thenReturn(null);

        mvc.perform(post("/api/channels/{handle}/add", "ghost-channel")
                        .header("Authorization", bearer("free-ghost@example.test", "FREE"))
                        .param("channelName", "Spam"))
                .andExpect(status().isBadRequest());

        assertThat(channelRepository.findByHandle("ghost-channel")).isEmpty();
    }

    @Test
    void freeUserCannotCreateAChannelWithoutVideoTitles() throws Exception {
        when(youTubeApiService.resolveChannelByHandle("empty-channel")).thenReturn(resolved("empty-channel", "Empty Channel"));
        when(youTubeApiService.getRecentVideoTitles(eq("empty-channel"), anyInt())).thenReturn(List.of());

        mvc.perform(post("/api/channels/{handle}/add", "empty-channel")
                        .header("Authorization", bearer("free-empty@example.test", "FREE"))
                        .param("channelName", "Stock picks every day"))
                .andExpect(status().isBadRequest());

        assertThat(channelRepository.findByHandle("empty-channel")).isEmpty();
    }

    @Test
    void freeUserAdditionUsesMetadataResolvedFromYouTube() throws Exception {
        when(youTubeApiService.resolveChannelByHandle("real-picker")).thenReturn(resolved("real-picker", "Real Picker"));
        when(youTubeApiService.getRecentVideoTitles(eq("real-picker"), anyInt())).thenReturn(List.of("Why I bought AAPL"));
        when(aiModelService.scoreChannelRelevance(eq("Real Picker"), anyList())).thenReturn(8);

        mvc.perform(post("/api/channels/{handle}/add", "real-picker")
                        .header("Authorization", bearer("free-real@example.test", "FREE"))
                        .param("channelName", "Spoofed name")
                        .param("description", "Spoofed description")
                        .param("subscriberCount", "999999999")
                        .param("youtubeChannelId", "UCspoofed"))
                .andExpect(status().isOk());

        Channel created = channelRepository.findByHandle("real-picker").orElseThrow();
        assertThat(created.getChannelName()).isEqualTo("Real Picker");
        assertThat(created.getDescription()).isEqualTo("Resolved description");
        assertThat(created.getSubscriberCount()).isEqualTo(1234L);
        assertThat(created.getYoutubeChannelId()).isEqualTo("UCresolved");
        assertThat(created.getApprovalSource()).isEqualTo("AUTO");
    }

    @Test
    void anonymousCallerCannotAddAChannel() throws Exception {
        mvc.perform(post("/api/channels/{handle}/add", "anon-channel").param("channelName", "Anon"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanStillUpdateAnExistingChannel() throws Exception {
        Channel existing = channelRepository.findAll().getLast();
        String originalName = existing.getChannelName();

        mvc.perform(post("/api/channels/{handle}/add", existing.getHandle())
                        .header("Authorization", bearer("admin-update@example.test", "ADMIN"))
                        .param("channelName", originalName)
                        .param("description", "Updated by admin"))
                .andExpect(status().isOk());

        assertThat(channelRepository.findByHandle(existing.getHandle()).orElseThrow().getDescription())
                .isEqualTo("Updated by admin");
    }

    private ChannelSearchResultDto resolved(String handle, String channelName) {
        return new ChannelSearchResultDto(handle, channelName, "https://www.youtube.com/@" + handle,
                null, "Resolved description", 1234L, 10L, "2020-01-01", "UCresolved");
    }
}
