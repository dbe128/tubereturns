package com.tubereturns.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptDownloadServiceIT {

    private static final String VIDEO_ID = "9hjbOci_gIg";

    @Test
    void transcript_isDownloadedAndComplete() throws Exception {
        TranscriptDownloadService service = new TranscriptDownloadService(null, null);
        ReflectionTestUtils.setField(service, "ytDlpPath", "yt-dlp");
        ReflectionTestUtils.setField(service, "timeoutSeconds", 300);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "cookiesPath", "src/main/resources/cookies.txt");

        String transcript = service.executeYtDlp(VIDEO_ID);

        assertThat(transcript).isNotBlank();
        assertThat(transcript).endsWith("Thank you guys for watching this video.");
    }
}
