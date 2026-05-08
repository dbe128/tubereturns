package com.tubereturns.service;

import com.tubereturns.model.Channel;
import com.tubereturns.model.Pick;
import com.tubereturns.model.Stock;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockRepository;
import com.tubereturns.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
@Profile("dev")
public class MockChannelDataSeedService {

    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final StockRepository stockRepository;
    private final PickRepository pickRepository;
    private final MockChannelProvider mockChannelProvider;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void seedData() {
        for (MockChannelProvider.MockChannelData channelData : mockChannelProvider.getChannels()) {
            Channel channel = channelRepository.findByHandle(channelData.handle())
                .orElseGet(() -> {
                    Channel c = new Channel(channelData.handle(), channelData.channelName());
                    c.setDescription(channelData.description());
                    return channelRepository.save(c);
                });
            if (!channel.isDiscoveryComplete()) {
                channel.setDiscoveryComplete(true);
                channelRepository.save(channel);
            }

            for (MockChannelProvider.MockVideoData videoData : channelData.videos()) {
                if (videoRepository.existsByVideoId(videoData.videoId())) {
                    continue;
                }

                Video video = new Video(videoData.videoId(), channel, videoData.title(), videoData.publishedAt());
                video.setTranscriptStatus(Video.TranscriptStatus.DOWNLOADED);
                video.setProcessingStatus(Video.ProcessingStatus.COMPLETED);
                videoRepository.save(video);

                for (MockChannelProvider.MockPickData pickData : videoData.picks()) {
                    Stock stock = stockRepository.findByTickerSymbol(pickData.ticker())
                        .orElseGet(() -> stockRepository.save(new Stock(pickData.ticker(), pickData.companyName())));
                    pickRepository.save(new Pick(video, stock, Pick.Signal.valueOf(pickData.signal())));
                }

                log.info("Seeded mock video '{}' with {} picks", video.getTitle(), videoData.picks().size());
            }

            log.info("Seeded mock channel '{}'", channel.getChannelName());
        }
    }
}
