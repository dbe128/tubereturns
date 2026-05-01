package com.tubereturns.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "spring.profiles.active", matches = ".*dev.*")
class TranscriptDownloadServiceIT {

    private static final String VIDEO_ID = "9hjbOci_gIg";

    @Test
    void transcript_isDownloadedAndComplete() throws Exception {
        TranscriptDownloadService service = new TranscriptDownloadService(null, null);
        ReflectionTestUtils.setField(service, "ytbsdPath", "youtube_transcript_api");
        ReflectionTestUtils.setField(service, "timeoutSeconds", 120);
        ReflectionTestUtils.setField(service, "enabled", true);

        String transcript = service.fetchTranscript(VIDEO_ID);

        assertThat(transcript).isNotBlank();
        assertThat(transcript).endsWith("Thank you guys for watching this video.");
    }
}
