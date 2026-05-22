package com.tubereturns.controller;

import com.tubereturns.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class SitemapController {

    private final ChannelRepository channelRepository;

    @Value("${tubereturns.app.base-url}")
    private String baseUrl;

    @GetMapping(value = "/api/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        xml.append("  <url><loc>").append(baseUrl).append("/</loc></url>\n");
        channelRepository.findAll().forEach(c ->
                xml.append("  <url><loc>").append(baseUrl).append("/channel/")
                        .append(c.getHandle()).append("</loc></url>\n"));
        xml.append("</urlset>");
        return xml.toString();
    }
}
