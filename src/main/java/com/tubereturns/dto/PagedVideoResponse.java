package com.tubereturns.dto;

import java.util.List;

public record PagedVideoResponse(
    List<VideoSummaryDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
