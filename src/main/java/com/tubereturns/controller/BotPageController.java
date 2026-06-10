package com.tubereturns.controller;

import com.tubereturns.dto.ChannelResponseDto;
import com.tubereturns.dto.PickPerformanceDto;
import com.tubereturns.model.Channel;
import com.tubereturns.model.Pick;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockRepository;
import com.tubereturns.service.ChannelListService;
import com.tubereturns.service.PickPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/bot")
public class BotPageController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ChannelListService channelListService;
    private final ChannelRepository channelRepository;
    private final StockRepository stockRepository;
    private final PickRepository pickRepository;
    private final PickPerformanceService pickPerformanceService;

    @Value("${tubereturns.app.base-url}")
    private String baseUrl;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Crawler-readable HTML snapshot of the leaderboard")
    public String leaderboard() {
        List<ChannelResponseDto> channels = channelListService.getAllChannels().stream()
                .sorted(Comparator.comparing(ChannelResponseDto::score1y,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        StringBuilder body = new StringBuilder();
        body.append("<h1>TubeReturns — Finance YouTubers Ranked by Real Stock-Pick Returns</h1>\n");
        body.append("<p>TubeReturns tracks every explicit BUY recommendation from finance YouTube channels, ")
                .append("measures the actual return of each pick from the video's publication date, and ranks channels ")
                .append("by alpha — the excess return versus buying the S&P 500 on the same day.</p>\n");
        body.append("<h2>Leaderboard</h2>\n");
        body.append("<table>\n<thead><tr><th>Rank</th><th>Channel</th><th>1-Year Alpha</th><th>1-Month Alpha</th><th>3-Year Alpha</th><th>Eligible 1Y Picks</th><th>Subscribers</th></tr></thead>\n<tbody>\n");
        int rank = 1;
        for (ChannelResponseDto c : channels) {
            body.append("<tr><td>").append(rank++).append("</td><td><a href=\"/channel/")
                    .append(slugOf(c)).append("\">").append(esc(c.channelName())).append("</a></td><td>")
                    .append(pct(c.score1y())).append("</td><td>")
                    .append(pct(c.score1m())).append("</td><td>")
                    .append(pct(c.score3y())).append("</td><td>")
                    .append(c.eligible1y()).append("</td><td>")
                    .append(c.subscriberCount() != null ? String.format(Locale.ENGLISH, "%,d", c.subscriberCount()) : "—")
                    .append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n");
        body.append("<p>Alpha is the channel's average pick return minus the S&P 500 return over the identical period. ")
                .append("Only picks old enough for the window to have closed are counted.</p>\n");
        body.append("<p><a href=\"/faq\">FAQ</a> · <a href=\"/llms.txt\">Machine-readable summary</a></p>\n");

        return page(
                "TubeReturns — Stock-Picking YouTubers Ranked by Returns",
                "Finance YouTubers ranked by the real performance of their stock picks — 1-month, 1-year, and 3-year alpha vs the S&P 500.",
                baseUrl + "/",
                body.toString());
    }

    @GetMapping(value = "/channel/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Crawler-readable HTML snapshot of a channel page")
    public ResponseEntity<String> channel(@PathVariable String slug) {
        Optional<Channel> channelOpt = channelRepository.findByNameSlug(slug)
                .or(() -> channelRepository.findBySlugOrHandle(slug));
        if (channelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Channel channel = channelOpt.get();
        PickPerformanceService.ChannelScoreResult score = pickPerformanceService.computeScoreForChannel(channel.getId());
        List<PickPerformanceDto> picks = pickPerformanceService.computeForChannel(channel.getId());

        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(esc(channel.getChannelName())).append(" — Stock Pick Performance</h1>\n");
        if (channel.getDescription() != null && !channel.getDescription().isBlank()) {
            body.append("<p>").append(esc(channel.getDescription())).append("</p>\n");
        }
        body.append("<h2>Alpha vs S&P 500</h2>\n<ul>\n");
        body.append("<li>1-month alpha: ").append(pct(score.score1m())).append(" (").append(score.eligible1m()).append(" eligible picks)</li>\n");
        body.append("<li>1-year alpha: ").append(pct(score.score1y())).append(" (").append(score.eligible1y()).append(" eligible picks)</li>\n");
        body.append("<li>3-year alpha: ").append(pct(score.score3y())).append(" (").append(score.eligible3y()).append(" eligible picks)</li>\n");
        body.append("</ul>\n");
        body.append("<h2>All picks (").append(picks.size()).append(")</h2>\n");
        body.append("<table>\n<thead><tr><th>Ticker</th><th>Company</th><th>Video</th><th>Date</th><th>1M Return</th><th>1Y Return</th><th>3Y Return</th><th>1Y Alpha</th></tr></thead>\n<tbody>\n");
        for (PickPerformanceDto p : picks) {
            if (p.unknown()) {
                continue;
            }
            body.append("<tr><td><a href=\"/stock/").append(esc(p.tickerSymbol())).append("\">")
                    .append(esc(p.tickerSymbol())).append("</a></td><td>")
                    .append(esc(nullable(p.companyName()))).append("</td><td><a href=\"https://youtu.be/")
                    .append(esc(p.videoId())).append("\">").append(esc(nullable(p.videoTitle()))).append("</a></td><td>")
                    .append(date(p.videoPublishedAt())).append("</td><td>")
                    .append(pct(p.return1m())).append("</td><td>")
                    .append(pct(p.return1y())).append("</td><td>")
                    .append(pct(p.return3y())).append("</td><td>")
                    .append(pct(p.alpha1y())).append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n");
        body.append("<p><a href=\"/\">Back to the full leaderboard</a></p>\n");

        String alphaText = score.score1y() != null ? pct(score.score1y()) + " 1-year alpha vs the S&P 500" : "tracked stock picks measured vs the S&P 500";
        return ResponseEntity.ok(page(
                esc(channel.getChannelName()) + " — Stock Pick Performance | TubeReturns",
                esc(channel.getChannelName()) + ": " + alphaText + " across " + picks.size() + " picks. See every recommendation and its actual return.",
                baseUrl + "/channel/" + channel.getNameSlug(),
                body.toString()));
    }

    @GetMapping(value = "/stock/{ticker}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Crawler-readable HTML snapshot of a stock page")
    public ResponseEntity<String> stock(@PathVariable String ticker) {
        return stockRepository.findByTickerSymbol(ticker.toUpperCase())
                .filter(stock -> !stock.isUnknown())
                .map(stock -> {
                    List<Pick> picks = pickRepository.findByTickerWithChannel(stock.getTickerSymbol());
                    long channelCount = picks.stream().map(p -> p.getVideo().getChannel().getId()).distinct().count();
                    String name = stock.getCompanyName() != null ? stock.getCompanyName() : stock.getTickerSymbol();

                    StringBuilder body = new StringBuilder();
                    body.append("<h1>").append(esc(name)).append(" (").append(esc(stock.getTickerSymbol()))
                            .append(") — Which YouTubers Picked It?</h1>\n");
                    body.append("<p>").append(picks.size()).append(" BUY recommendations from ").append(channelCount)
                            .append(" finance YouTube channel").append(channelCount == 1 ? "" : "s")
                            .append(", with the actual return of each pick measured from the video's publication date.</p>\n");
                    body.append("<table>\n<thead><tr><th>Channel</th><th>Video</th><th>Date</th><th>1M Return</th><th>1Y Return</th><th>3Y Return</th><th>1Y Alpha</th></tr></thead>\n<tbody>\n");
                    for (Pick p : picks) {
                        body.append("<tr><td><a href=\"/channel/").append(esc(p.getVideo().getChannel().getNameSlug()))
                                .append("\">").append(esc(p.getVideo().getChannel().getChannelName())).append("</a></td><td><a href=\"https://youtu.be/")
                                .append(esc(p.getVideo().getVideoId())).append("\">").append(esc(nullable(p.getVideo().getTitle()))).append("</a></td><td>")
                                .append(date(p.getVideo().getPublishedAt())).append("</td><td>")
                                .append(pct(p.getReturn1m())).append("</td><td>")
                                .append(pct(p.getReturn1y())).append("</td><td>")
                                .append(pct(p.getReturn3y())).append("</td><td>")
                                .append(pct(p.getAlpha1y())).append("</td></tr>\n");
                    }
                    body.append("</tbody>\n</table>\n");
                    body.append("<p><a href=\"/\">Back to the full leaderboard</a></p>\n");

                    return ResponseEntity.ok(page(
                            esc(name) + " (" + esc(stock.getTickerSymbol()) + ") — YouTuber Stock Picks | TubeReturns",
                            "Which finance YouTubers recommended " + esc(name) + " and how did it go? " + picks.size()
                                    + " picks from " + channelCount + " channels with real returns vs the S&P 500.",
                            baseUrl + "/stock/" + stock.getTickerSymbol(),
                            body.toString()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String page(String title, String description, String canonical, String body) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <title>%s</title>
                <meta name="description" content="%s">
                <meta name="robots" content="index, follow">
                <link rel="canonical" href="%s">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="TubeReturns">
                <meta property="og:url" content="%s">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:image" content="%s/logo.webp">
                <meta name="twitter:card" content="summary_large_image">
                </head>
                <body>
                %s</body>
                </html>
                """.formatted(title, description, canonical, canonical, title, description, baseUrl, body);
    }

    private String slugOf(ChannelResponseDto c) {
        return Channel.nameSlugFor(c.channelName());
    }

    private String esc(String value) {
        return HtmlUtils.htmlEscape(value);
    }

    private String nullable(String value) {
        return value != null ? value : "";
    }

    private String date(Instant instant) {
        return instant != null ? DATE.format(instant) : "—";
    }

    private String pct(Double value) {
        return value != null ? String.format(Locale.ENGLISH, "%+.1f%%", value) : "—";
    }
}
