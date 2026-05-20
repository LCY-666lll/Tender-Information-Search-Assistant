package com.xfusion.bidaggregator.model;

import java.util.ArrayList;
import java.util.List;

public class HarvestResult {
    private final List<BidItem> rawItems;
    private final List<SourceStatus> sourceStatuses;

    public HarvestResult(List<BidItem> rawItems, List<SourceStatus> sourceStatuses) {
        this.rawItems = rawItems == null ? new ArrayList<>() : rawItems;
        this.sourceStatuses = sourceStatuses == null ? new ArrayList<>() : sourceStatuses;
    }

    public List<BidItem> getRawItems() {
        return rawItems;
    }

    public List<SourceStatus> getSourceStatuses() {
        return sourceStatuses;
    }
}
