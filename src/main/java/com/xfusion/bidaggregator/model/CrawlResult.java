package com.xfusion.bidaggregator.model;

import java.util.ArrayList;
import java.util.List;

public class CrawlResult {
    private SourceStatus status;
    private List<BidItem> items = new ArrayList<>();

    public CrawlResult(SourceStatus status, List<BidItem> items) {
        this.status = status;
        this.items = items;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public List<BidItem> getItems() {
        return items;
    }
}
