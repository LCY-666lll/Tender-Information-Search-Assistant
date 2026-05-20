package com.xfusion.bidaggregator.model;

import java.util.ArrayList;
import java.util.List;

public class CurationResult {
    private final List<BidItem> items;
    private final List<BidItem> candidateItems;
    private final int duplicateCount;
    private final int incrementalSkipped;

    public CurationResult(List<BidItem> items, int duplicateCount, int incrementalSkipped) {
        this(items, new ArrayList<>(), duplicateCount, incrementalSkipped);
    }

    public CurationResult(List<BidItem> items, List<BidItem> candidateItems, int duplicateCount, int incrementalSkipped) {
        this.items = items == null ? new ArrayList<>() : items;
        this.candidateItems = candidateItems == null ? new ArrayList<>() : candidateItems;
        this.duplicateCount = Math.max(duplicateCount, 0);
        this.incrementalSkipped = Math.max(incrementalSkipped, 0);
    }

    public List<BidItem> getItems() {
        return items;
    }

    public List<BidItem> getCandidateItems() {
        return candidateItems;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public int getIncrementalSkipped() {
        return incrementalSkipped;
    }
}
